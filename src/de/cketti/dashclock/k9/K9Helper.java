package de.cketti.dashclock.k9;


import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;


/**
 * Helper class to interface with K-9 Mail.
 */
public class K9Helper {
    /**
     * Permission required to access K-9 Mail's public content provider.
     */
    public static final String PERMISSION = "com.fsck.k9.permission.READ_MESSAGES";

    /**
     * Authority of K-9 Mail's content provider.
     */
    public static final String AUTHORITY = "com.fsck.k9.messageprovider";

    /**
     * Base URI of K-9 Mail's content provider.
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * The content:// style URI to access K-9 Mail's accounts.
     */
    public static final Uri ACCOUNTS_URI = Uri.withAppendedPath(CONTENT_URI, "accounts");

    /**
     * The content:// style base URI to access the unread count of an account.
     */
    public static final Uri ACCOUNT_UNREAD_URI =
            Uri.withAppendedPath(CONTENT_URI, "account_unread");


    private static final Uri getAccountUnreadUri(int accountNumber) {
        return Uri.withAppendedPath(ACCOUNT_UNREAD_URI, Integer.toString(accountNumber));
    }

    /**
     * Column names for the accounts "table".
     *
     * @see K9Helper#ACCOUNTS_URI
     */
    public interface AccountColumns {
        /**
         * Account number.
         *
         * <p><strong>Note:</strong> This value will change when reordering the accounts in
         * K-9 Mail. Use {@link #UUID} when saving references to an account.</p>
         *
         * <p>Type: INTEGER</p>
         */
        public static final String NUMBER = "accountNumber";

        /**
         * Name of the account.
         *
         * <p>Type: TEXT</p>
         */
        public static final String NAME = "accountName";

        /**
         * The account's UUID.
         *
         * <p>Type: TEXT</p>
         */
        public static final String UUID = "accountUuid";

        /**
         * The RGB color code of the account's color.
         *
         * <p>Type: INTEGER</p>
         */
        public static final String COLOR = "accountColor";
    }

    /**
     * Column names for an account's unread count "table".
     *
     * <p><strong>Note:</strong> The projection supplied to the content resolver is ignored by
     * K-9 Mail. Instead {@link #NAME} is always the first column, {@link #UNREAD} the second.
     * You can use the constants {@link #ACCOUNT_NAME_INDEX} and {@link #UNREAD_INDEX} to access
     * the columns.
     * </p>
     *
     * @see K9Helper#ACCOUNT_UNREAD_URI
     */
    public interface AccountUnreadColumns {
        /**
         * Name of the account.
         *
         * <p>Type: TEXT</p>
         */
        public static final String NAME = "accountName";

        /**
         * Number of unread messages in this account.
         *
         * <p>Type: INTEGER</p>
         */
        public static final String UNREAD = "unread";

        /**
         * Index of the {@link #NAME} column.
         */
        public static final int ACCOUNT_NAME_INDEX = 0;

        /**
         * Index of the {@link #UNREAD} column.
         */
        public static final int UNREAD_INDEX = 1;
    }

    /**
     * Constants related to broadcast intents sent by K-9 Mail.
     */
    public interface BroadcastIntents {
        /**
         * Broadcast Action: Sent when an email was received.
         */
        public static final String ACTION_EMAIL_RECEIVED =
                "com.fsck.k9.intent.action.EMAIL_RECEIVED";

        /**
         * Broadcast Action: Sent when an email was deleted.
         */
        public static final String ACTION_EMAIL_DELETED =
                "com.fsck.k9.intent.action.EMAIL_DELETED";

        /**
         * Broadcast Action: Sent in various situations when external applications should re-query
         * K-9 Mail's content provider(s).
         */
        public static final String ACTION_REFRESH_OBSERVER =
                "com.fsck.k9.intent.action.REFRESH_OBSERVER";
    }

    /**
     * Stores information about a K-9 Mail account.
     */
    public static class Account {
        /**
         * The account number.
         */
        public final int number;

        /**
         * The name of the account.
         */
        public final String name;

        /**
         * The account's UUID.
         */
        public final String uuid;

        /**
         * RGB value of the account's color.
         */
        public final int color;

        Account(int number, String name, String uuid, int color) {
            this.number = number;
            this.name = name;
            this.uuid = uuid;
            this.color = color;
        }

        /**
         * Returns the account's name.
         */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Queries K-9 Mail's content provider to retrieve a list of accounts.
     *
     * <p>This should not be called from the main thread.</p>
     *
     * @param context
     *         Used to retrieve the content resolver.
     *
     * @return A (possibly empty) list of {@link Account} instances, or {@code null} in case of an
     *         error.
     */
    public static final List<Account> getAccounts(Context context) {
        try {
            ContentResolver cr = context.getContentResolver();

            String[] projection = {
                    AccountColumns.NUMBER,
                    AccountColumns.NAME,
                    AccountColumns.UUID,
                    AccountColumns.COLOR
            };

            Cursor cursor = cr.query(ACCOUNTS_URI, projection, null, null, null, null);
            if (cursor == null) {
                return null;
            }

            List<Account> accounts = new ArrayList<Account>();
            try {
                while (cursor.moveToNext()) {
                    int accountNumber = cursor.getInt(0);
                    String accountName = cursor.getString(1);
                    String accountUuid = cursor.getString(2);
                    int accountColor = cursor.getInt(3);

                    accounts.add(new Account(accountNumber, accountName, accountUuid, accountColor));
                }
            } finally {
                cursor.close();
            }

            return accounts;
        } catch (Exception e) {
            Log.e("K9Helper", "Something went wrong while fetching the list of accounts", e);
            return null;
        }
    }

    /**
     * Query K-9 Mail's content provider to retrieve the number of unread messages in the supplied
     * account.
     *
     * @param context
     *         Used to retrieve the content resolver.
     * @param account
     *         The account to get the unread messages for. Use {@link #getAccounts(Context)} to
     *         retrieve an {@link Account} instance. Must not be {@code null}.
     *
     * @return The number of unread messages in that account. Or {@code 0} if something went wrong.
     */
    public static final int getUnreadCount(Context context, Account account) {
        try {
            ContentResolver cr = context.getContentResolver();
            Cursor cursor = cr.query(K9Helper.getAccountUnreadUri(account.number), null, null, null,
                    null, null);

            if (cursor == null) {
                return 0;
            }

            int accountUnread = 0;
            try {
                if (cursor.moveToFirst()) {
                    accountUnread = cursor.getInt(AccountUnreadColumns.UNREAD_INDEX);
                }
            } finally {
                cursor.close();
            }

            return accountUnread;
        } catch (Exception e) {
            Log.e("K9Helper", "Something went wrong while fetching the unread count for " +
                    account.name + " (" + account.uuid + ")", e);
            return 0;
        }
    }

    /**
     * Returns an intent to start K-9 Mail.
     *
     * @param context
     *         Used to retrieve the package manager.
     *
     * @return An intent to start K-9 Mail's main activity, or {@code null} in case of an error.
     */
    public static final Intent getStartK9Intent(Context context) {
        try {
            Intent intent = new Intent();
            PackageManager manager = context.getPackageManager();
            intent = manager.getLaunchIntentForPackage("com.fsck.k9");
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        } catch (Exception e) {
            return null;
        }
    }

}
package com.datang.smarthelmet.contacts;

/*
ContactsManager.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.ContactsContract;
import com.datang.smarthelmet.R;
import com.datang.smarthelmet.SmartHelmetManager;
import com.datang.smarthelmet.SmartHelmetService;
import com.datang.smarthelmet.compatibility.Compatibility;
import com.datang.smarthelmet.settings.LinphonePreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.linphone.core.Address;
import org.linphone.core.Core;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.FriendListListener;
import org.linphone.core.MagicSearch;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;

public class ContactsManager extends ContentObserver implements FriendListListener {
    private List<SmartHelmetContact> mContacts, mSipContacts;
    private final ArrayList<ContactsUpdatedListener> mContactsUpdatedListeners;
    private MagicSearch mMagicSearch;
    private boolean mContactsFetchedOnce = false;
    private Context mContext;
    private AsyncContactsLoader mLoadContactTask;
    private boolean mInitialized = false;

    public static ContactsManager getInstance() {
        return SmartHelmetService.instance().getContactsManager();
    }

    public ContactsManager(Context context, Handler handler) {
        super(handler);
        mContext = context;
        mContactsUpdatedListeners = new ArrayList<>();
        mContacts = new ArrayList<>();
        mSipContacts = new ArrayList<>();

        if (SmartHelmetManager.getCore() != null) {
            mMagicSearch = SmartHelmetManager.getCore().createMagicSearch();
            mMagicSearch.setLimitedSearch(false); // Do not limit the number of results
        }
    }

    public void addContactsListener(ContactsUpdatedListener listener) {
        mContactsUpdatedListeners.add(listener);
    }

    public void removeContactsListener(ContactsUpdatedListener listener) {
        mContactsUpdatedListeners.remove(listener);
    }

    public ArrayList<ContactsUpdatedListener> getContactsListeners() {
        return mContactsUpdatedListeners;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        Log.i("[Contacts Manager] Content observer detected a changing in at least one contact");
        fetchContactsAsync();
    }

    public synchronized List<SmartHelmetContact> getContacts() {
        return mContacts;
    }

    synchronized void setContacts(List<SmartHelmetContact> c) {
        mContacts = c;
    }

    public synchronized List<SmartHelmetContact> getSIPContacts() {
        return mSipContacts;
    }

    synchronized void setSipContacts(List<SmartHelmetContact> c) {
        mSipContacts = c;
    }

    public void destroy() {
        mContext.getContentResolver().unregisterContentObserver(this);

        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }
        // SmartHelmetContact has a Friend field and Friend can have a SmartHelmetContact has
        // userData
        // Friend also keeps a ref on the Core, so we have to clean them
        for (SmartHelmetContact c : mContacts) {
            c.setFriend(null);
        }
        mContacts.clear();
        for (SmartHelmetContact c : mSipContacts) {
            c.setFriend(null);
        }
        mSipContacts.clear();

        Core core = SmartHelmetManager.getCore();
        if (core != null) {
            for (FriendList list : core.getFriendsLists()) {
                list.removeListener(this);
            }
        }
    }

    public void fetchContactsAsync() {
        if (mLoadContactTask != null) {
            mLoadContactTask.cancel(true);
        }
        if (!hasReadContactsAccess()) {
            Log.w("[Contacts Manager] Can't fetch contact without READ permission");
            return;
        }
        mLoadContactTask = new AsyncContactsLoader(mContext);
        mContactsFetchedOnce = true;
        mLoadContactTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    public MagicSearch getMagicSearch() {
        return mMagicSearch;
    }

    public boolean contactsFetchedOnce() {
        return mContactsFetchedOnce;
    }

    public List<SmartHelmetContact> getContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<SmartHelmetContact> searchContactsBegin = new ArrayList<>();
        List<SmartHelmetContact> searchContactsContain = new ArrayList<>();
        for (SmartHelmetContact contact : getContacts()) {
            if (contact.getFullName() != null) {
                if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
                    searchContactsBegin.add(contact);
                } else if (contact.getFullName()
                        .toLowerCase(Locale.getDefault())
                        .contains(search)) {
                    searchContactsContain.add(contact);
                }
            }
        }
        searchContactsBegin.addAll(searchContactsContain);
        return searchContactsBegin;
    }

    public List<SmartHelmetContact> getSIPContacts(String search) {
        search = search.toLowerCase(Locale.getDefault());
        List<SmartHelmetContact> searchContactsBegin = new ArrayList<>();
        List<SmartHelmetContact> searchContactsContain = new ArrayList<>();
        for (SmartHelmetContact contact : getSIPContacts()) {
            if (contact.getFullName() != null) {
                if (contact.getFullName().toLowerCase(Locale.getDefault()).startsWith(search)) {
                    searchContactsBegin.add(contact);
                } else if (contact.getFullName()
                        .toLowerCase(Locale.getDefault())
                        .contains(search)) {
                    searchContactsContain.add(contact);
                }
            }
        }
        searchContactsBegin.addAll(searchContactsContain);
        return searchContactsBegin;
    }

    public void enableContactsAccess() {
        LinphonePreferences.instance().disableFriendsStorage();
    }

    public boolean hasReadContactsAccess() {
        if (mContext == null) {
            return false;
        }
        boolean contactsR =
                (PackageManager.PERMISSION_GRANTED
                        == mContext.getPackageManager()
                                .checkPermission(
                                        android.Manifest.permission.READ_CONTACTS,
                                        mContext.getPackageName()));
        return contactsR
                && !mContext.getResources().getBoolean(R.bool.force_use_of_linphone_friends);
    }

    private boolean hasWriteContactsAccess() {
        if (mContext == null) {
            return false;
        }
        return (PackageManager.PERMISSION_GRANTED
                == mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.WRITE_CONTACTS, mContext.getPackageName()));
    }

    private boolean hasWriteSyncPermission() {
        if (mContext == null) {
            return false;
        }
        return (PackageManager.PERMISSION_GRANTED
                == mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.WRITE_SYNC_SETTINGS,
                                mContext.getPackageName()));
    }

    public boolean isLinphoneContactsPrefered() {
        ProxyConfig lpc = SmartHelmetManager.getCore().getDefaultProxyConfig();
        return lpc != null
                && lpc.getIdentityAddress()
                        .getDomain()
                        .equals(mContext.getString(R.string.default_domain));
    }

    public void initializeContactManager() {
        if (!mInitialized) {
            if (mContext.getResources().getBoolean(R.bool.use_linphone_tag)) {
                if (hasReadContactsAccess()
                        && hasWriteContactsAccess()
                        && hasWriteSyncPermission()) {
                    if (SmartHelmetService.isReady()) {
                        initializeSyncAccount();
                        mInitialized = true;
                    }
                }
            }
        }

        if (mContext != null && getContacts().isEmpty() && hasReadContactsAccess()) {
            fetchContactsAsync();
        }
    }

    private void makeContactAccountVisible() {
        ContentProviderClient client =
                mContext.getContentResolver()
                        .acquireContentProviderClient(ContactsContract.AUTHORITY_URI);
        ContentValues values = new ContentValues();
        values.put(
                ContactsContract.Settings.ACCOUNT_NAME,
                mContext.getString(R.string.sync_account_name));
        values.put(
                ContactsContract.Settings.ACCOUNT_TYPE,
                mContext.getString(R.string.sync_account_type));
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true);
        try {
            client.insert(
                    ContactsContract.Settings.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .build(),
                    values);
            Log.i("[Contacts Manager] Contacts account made visible");
        } catch (RemoteException e) {
            Log.e("[Contacts Manager] Couldn't make contacts account visible: " + e);
        }
        Compatibility.closeContentProviderClient(client);
    }

    private void initializeSyncAccount() {
        AccountManager accountManager =
                (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);

        Account[] accounts =
                accountManager.getAccountsByType(mContext.getString(R.string.sync_account_type));

        if (accounts != null && accounts.length == 0) {
            Account newAccount =
                    new Account(
                            mContext.getString(R.string.sync_account_name),
                            mContext.getString(R.string.sync_account_type));
            try {
                accountManager.addAccountExplicitly(newAccount, null, null);
                Log.i("[Contacts Manager] Contact account added");
                makeContactAccountVisible();
            } catch (Exception e) {
                Log.e("[Contacts Manager] Couldn't initialize sync account: " + e);
            }
        } else if (accounts != null) {
            for (Account account : accounts) {
                Log.i(
                        "[Contacts Manager] Found account with name \""
                                + account.name
                                + "\" and type \""
                                + account.type
                                + "\"");
                makeContactAccountVisible();
            }
        }
    }

    public String getAndroidContactIdFromUri(Uri uri) {
        String[] projection = {ContactsContract.CommonDataKinds.SipAddress.CONTACT_ID};
        Cursor cursor =
                mContext.getApplicationContext()
                        .getContentResolver()
                        .query(uri, projection, null, null, null);
        cursor.moveToFirst();

        int nameColumnIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.CONTACT_ID);
        String id = cursor.getString(nameColumnIndex);
        cursor.close();
        return id;
    }

    public synchronized SmartHelmetContact findContactFromAndroidId(String androidId) {
        if (androidId == null) {
            return null;
        }

        for (SmartHelmetContact c : getContacts()) {
            if (c.getAndroidId() != null && c.getAndroidId().equals(androidId)) {
                return c;
            }
        }
        return null;
    }

    public synchronized SmartHelmetContact findContactFromAddress(Address address) {
        if (address == null) return null;
        Core core = SmartHelmetManager.getCore();

        Friend lf = core.findFriend(address);
        if (lf != null) {
            return (SmartHelmetContact) lf.getUserData();
        }

        String username = address.getUsername();
        if (android.util.Patterns.PHONE.matcher(username).matches()) {
            return findContactFromPhoneNumber(username);
        }

        return null;
    }

    public synchronized SmartHelmetContact findContactFromPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        if (!android.util.Patterns.PHONE.matcher(phoneNumber).matches()) {
            Log.w(
                    "[Contacts Manager] Expected phone number but doesn't look like it: "
                            + phoneNumber);
            return null;
        }

        Core core = SmartHelmetManager.getCore();
        ProxyConfig lpc = null;
        if (core != null) {
            lpc = core.getDefaultProxyConfig();
        }
        if (lpc == null) {
            Log.i("[Contacts Manager] Couldn't find default proxy config...");
            return null;
        }

        String normalized = lpc.normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            Log.w(
                    "[Contacts Manager] Couldn't normalize phone number "
                            + phoneNumber
                            + ", default proxy config prefix is "
                            + lpc.getDialPrefix());
            normalized = phoneNumber;
        }

        Address addr = lpc.normalizeSipUri(normalized);
        if (addr == null) {
            Log.w("[Contacts Manager] Couldn't normalize SIP URI " + normalized);
            return null;
        }

        // Without this, the hashmap inside liblinphone won't find it...
        addr.setUriParam("user", "phone");
        Friend lf = core.findFriend(addr);
        if (lf != null) {
            return (SmartHelmetContact) lf.getUserData();
        }

        Log.w("[Contacts Manager] Couldn't find friend...");
        return null;
    }

    public String getAddressOrNumberForAndroidContact(ContentResolver resolver, Uri contactUri) {
        // Phone Numbers
        String[] projection = new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER};
        Cursor c = resolver.query(contactUri, projection, null, null, null);
        if (c != null) {
            if (c.moveToNext()) {
                int numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = c.getString(numberIndex);
                c.close();
                return number;
            }
        }
        c.close();

        projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
        c = resolver.query(contactUri, projection, null, null, null);
        if (c != null) {
            if (c.moveToNext()) {
                int numberIndex =
                        c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
                String address = c.getString(numberIndex);
                c.close();
                return address;
            }
        }
        c.close();
        return null;
    }

    private synchronized boolean refreshSipContact(Friend lf) {
        SmartHelmetContact contact = (SmartHelmetContact) lf.getUserData();

        if (contact != null) {
            if (SmartHelmetService.instance().getResources().getBoolean(R.bool.use_linphone_tag)) {
                // Inserting Linphone information in Android contact if the parameter is enabled
                if (LinphonePreferences.instance()
                        .isPresenceStorageInNativeAndroidContactEnabled()) {
                    // add presence to native contact
                    AsyncContactPresence asyncContactPresence = new AsyncContactPresence(contact);
                    asyncContactPresence.execute();
                }
            }

            if (!mSipContacts.contains(contact)) {
                mSipContacts.add(contact);
                return true;
            }
        }

        return false;
    }

    public void delete(String id) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(id);
        deleteMultipleContactsAtOnce(ids);
    }

    public void deleteMultipleContactsAtOnce(List<String> ids) {
        String select = ContactsContract.Data.CONTACT_ID + " = ?";
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        for (String id : ids) {
            String[] args = new String[] {id};
            ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                            .withSelection(select, args)
                            .build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            Log.e("[Contacts Manager] " + e);
        }

        // To ensure removed contacts won't appear in the contacts list anymore
        fetchContactsAsync();
    }

    public String getString(int resourceID) {
        if (mContext == null) return null;
        return mContext.getString(resourceID);
    }

    @Override
    public void onContactCreated(FriendList list, Friend lf) {}

    @Override
    public void onContactDeleted(FriendList list, Friend lf) {}

    @Override
    public void onContactUpdated(FriendList list, Friend newFriend, Friend oldFriend) {}

    @Override
    public void onSyncStatusChanged(FriendList list, FriendList.SyncStatus status, String msg) {}

    @Override
    public void onPresenceReceived(FriendList list, Friend[] friends) {
        boolean updated = false;

        for (Friend lf : friends) {
            boolean newContact = refreshSipContact(lf);

            if (newContact) {
                updated = true;
            }
        }

        if (updated) {
            Collections.sort(mSipContacts);
        }

        for (ContactsUpdatedListener listener : mContactsUpdatedListeners) {
            listener.onContactsUpdated();
        }

        Compatibility.createChatShortcuts(mContext);
    }
}

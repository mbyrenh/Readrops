package com.readrops.api.services.freshrss;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.readrops.api.services.SyncResult;
import com.readrops.api.services.SyncType;
import com.readrops.api.services.freshrss.json.FreshRSSUserInfo;
import com.readrops.db.entities.Feed;
import com.readrops.db.entities.Folder;
import com.readrops.db.entities.Item;

import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import io.reactivex.Completable;
import io.reactivex.Single;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class FreshRSSDataSource {

    private static final int MAX_ITEMS = 5000;

    public static final String GOOGLE_READ = "user/-/state/com.google/read";
    public static final String GOOGLE_STARRED = "user/-/state/com.google/starred";

    private static final String FEED_PREFIX = "feed/";

    private FreshRSSService api;

    public FreshRSSDataSource(FreshRSSService api) {
        this.api = api;
    }

    /**
     * Call token API to generate a new token from account credentials
     *
     * @param login    login
     * @param password password
     * @return the generated token
     */
    public Single<String> login(@NonNull String login, @NonNull String password) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("Email", login)
                .addFormDataPart("Passwd", password)
                .build();

        return api.login(requestBody)
                .flatMap(response -> {
                    Properties properties = new Properties();
                    properties.load(new StringReader(response.string()));

                    return Single.just(properties.getProperty("Auth"));
                });
    }

    /**
     * Get a write token to modify feeds, folders and items on the server
     *
     * @return the write token generated by the server
     */
    public Single<String> getWriteToken() {
        return api.getWriteToken()
                .flatMap(responseBody -> Single.just(responseBody.string()));
    }

    /**
     * Retrieve user information : name, email, id, profileId
     *
     * @return user information
     */
    public Single<FreshRSSUserInfo> getUserInfo() {
        return api.getUserInfo();
    }

    /**
     * Synchronize feeds, folders, items and push read/unread items
     *
     * @param syncType   INITIAL or CLASSIC
     * @param syncData   data to sync (read/unread items ids, lastModified timestamp)
     * @param writeToken token for making modifications on the server
     * @return the result of the synchronization
     */
    public Single<SyncResult> sync(@NonNull SyncType syncType, @NonNull FreshRSSSyncData syncData, @NonNull String writeToken) {
        SyncResult syncResult = new SyncResult();

        return setItemsReadState(syncData, writeToken)
                .andThen(setItemsStarState(syncData, writeToken))
                .andThen(getFolders()
                        .flatMap(freshRSSFolders -> {
                            syncResult.setFolders(freshRSSFolders);

                            return getFeeds();
                        })
                        .flatMap(freshRSSFeeds -> {
                            syncResult.setFeeds(freshRSSFeeds);

                            if (syncType == SyncType.INITIAL_SYNC) {
                                return getItems(GOOGLE_READ, MAX_ITEMS, null);
                            } else {
                                return getItems(null, MAX_ITEMS, syncData.getLastModified());
                            }
                        })
                        .flatMap(freshRSSItems -> {
                            syncResult.setItems(freshRSSItems);

                            return Single.just(syncResult);
                        }));
    }

    /**
     * Fetch the feeds folders
     *
     * @return the feeds folders
     */
    public Single<List<Folder>> getFolders() {
        return api.getFolders();
    }

    /**
     * Fetch the feeds
     *
     * @return the feeds
     */
    public Single<List<Feed>> getFeeds() {
        return api.getFeeds();
    }

    /**
     * Fetch the items
     *
     * @param excludeTarget type of items to exclude (currently only read items)
     * @param max           max number of items to fetch
     * @param lastModified  fetch only items created after this timestamp
     * @return the items
     */
    public Single<List<Item>> getItems(@Nullable String excludeTarget, int max, @Nullable Long lastModified) {
        return api.getItems(excludeTarget, max, lastModified);
    }


    /**
     * Mark items read or unread
     *
     * @param read    true for read, false for unread
     * @param itemIds items ids to mark
     * @param token   token for modifications
     * @return Completable
     */
    public Completable markItemsReadUnread(boolean read, @NonNull List<String> itemIds, @NonNull String token) {
        if (read) {
            return api.setItemsState(token, GOOGLE_READ, null, itemIds);
        } else {
            return api.setItemsState(token, null, GOOGLE_READ, itemIds);
        }
    }

    /**
     * Mark items as starred or unstarred
     *
     * @param starred true for starred, false for unstarred
     * @param itemIds items ids to mark
     * @param token   token for modifications
     * @return Completable
     */
    public Completable markItemsStarredUnstarred(boolean starred, @NonNull List<String> itemIds, @NonNull String token) {
        if (starred) {
            return api.setItemsState(token, GOOGLE_STARRED, null, itemIds);
        } else {
            return api.setItemsState(token, null, GOOGLE_STARRED, itemIds);
        }
    }

    /**
     * Create a new feed
     *
     * @param token   token for modifications
     * @param feedUrl url of the feed to parse
     * @return Completable
     */
    public Completable createFeed(@NonNull String token, @NonNull String feedUrl) {
        return api.createOrDeleteFeed(token, FEED_PREFIX + feedUrl, "subscribe");
    }

    /**
     * Delete a feed
     *
     * @param token   token for modifications
     * @param feedUrl url of the feed to delete
     * @return Completable
     */
    public Completable deleteFeed(@NonNull String token, @NonNull String feedUrl) {
        return api.createOrDeleteFeed(token, FEED_PREFIX + feedUrl, "unsubscribe");
    }

    /**
     * Update feed title and folder
     *
     * @param token    token for modifications
     * @param feedUrl  url of the feed to update
     * @param title    new title
     * @param folderId id of the new folder
     * @return Completable
     */
    public Completable updateFeed(@NonNull String token, @NonNull String feedUrl, @NonNull String title, @NonNull String folderId) {
        return api.updateFeed(token, FEED_PREFIX + feedUrl, title, folderId, "edit");
    }

    /**
     * Create a new folder
     *
     * @param token   token for modifications
     * @param tagName name of the new folder
     * @return Completable
     */
    public Completable createFolder(@NonNull String token, @NonNull String tagName) {
        return api.createFolder(token, "user/-/label/" + tagName);
    }

    /**
     * Update folder name
     *
     * @param token    token for modifications
     * @param folderId id of the folder
     * @param name     new folder name
     * @return Completable
     */
    public Completable updateFolder(@NonNull String token, @NonNull String folderId, @NonNull String name) {
        return api.updateFolder(token, folderId, "user/-/label/" + name);
    }

    /**
     * Delete a folder
     *
     * @param token    token for modifications
     * @param folderId id of the folder to delete
     * @return Completable
     */
    public Completable deleteFolder(@NonNull String token, @NonNull String folderId) {
        return api.deleteFolder(token, folderId);
    }

    /**
     * Set items star state
     *
     * @param syncData data containing items to mark
     * @param token    token for modifications
     * @return A concatenation of two completable (read and unread completable)
     */
    private Completable setItemsReadState(@NonNull FreshRSSSyncData syncData, @NonNull String token) {
        Completable readItemsCompletable;
        if (syncData.getReadItemsIds().isEmpty()) {
            readItemsCompletable = Completable.complete();
        } else {
            readItemsCompletable = markItemsReadUnread(true, syncData.getReadItemsIds(), token);
        }

        Completable unreadItemsCompletable;
        if (syncData.getUnreadItemsIds().isEmpty()) {
            unreadItemsCompletable = Completable.complete();
        } else {
            unreadItemsCompletable = markItemsReadUnread(false, syncData.getUnreadItemsIds(), token);
        }

        return readItemsCompletable.concatWith(unreadItemsCompletable);
    }

    /**
     * Set items star state
     *
     * @param syncData data containing items to mark
     * @param token    token for modifications
     * @return A concatenation of two completable (starred and unstarred completable)
     */
    private Completable setItemsStarState(@NonNull FreshRSSSyncData syncData, @NonNull String token) {
        Completable starredItemsCompletable;
        if (syncData.getStarredItemsIds().isEmpty()) {
            starredItemsCompletable = Completable.complete();
        } else {
            starredItemsCompletable = markItemsStarredUnstarred(true, syncData.getStarredItemsIds(), token);
        }

        Completable unstarredItemsCompletable;
        if (syncData.getUnstarredItemsIds().isEmpty()) {
            unstarredItemsCompletable = Completable.complete();
        } else {
            unstarredItemsCompletable = markItemsStarredUnstarred(false, syncData.getUnstarredItemsIds(), token);
        }

        return starredItemsCompletable.concatWith(unstarredItemsCompletable);
    }
}

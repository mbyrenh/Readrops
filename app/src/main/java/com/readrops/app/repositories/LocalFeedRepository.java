package com.readrops.app.repositories;

import android.accounts.NetworkErrorException;
import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.support.annotation.Nullable;

import com.readrops.app.database.entities.Folder;
import com.readrops.app.database.pojo.FeedWithFolder;
import com.readrops.app.database.pojo.ItemWithFeed;
import com.readrops.app.database.entities.Feed;
import com.readrops.app.database.entities.Item;
import com.readrops.app.utils.FeedInsertionResult;
import com.readrops.app.utils.Utils;
import com.readrops.app.utils.HtmlParser;
import com.readrops.app.utils.ParsingResult;
import com.readrops.readropslibrary.Utils.LibUtils;
import com.readrops.readropslibrary.Utils.UnknownFormatException;
import com.readrops.readropslibrary.localfeed.AFeed;
import com.readrops.readropslibrary.localfeed.RSSQuery;
import com.readrops.readropslibrary.localfeed.RSSQueryResult;
import com.readrops.readropslibrary.localfeed.atom.ATOMFeed;
import com.readrops.readropslibrary.localfeed.json.JSONFeed;
import com.readrops.readropslibrary.localfeed.rss.RSSFeed;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

public class LocalFeedRepository extends ARepository {

    private static final String TAG = LocalFeedRepository.class.getSimpleName();

    private LiveData<List<ItemWithFeed>> itemsWhithFeed;

    public LocalFeedRepository(Application application) {
        super(application);

        itemsWhithFeed = database.itemDao().getAllItemWithFeeds();
    }

    public LiveData<List<ItemWithFeed>> getItemsWithFeed() {
        return itemsWhithFeed;
    }

    @Override
    public Observable<Feed> sync(@Nullable List<Feed> feeds) {
        return Observable.create(emitter -> {
            List<Feed> feedList;

            if (feeds == null || feeds.size() == 0)
                feedList = database.feedDao().getAllFeeds();
            else
                feedList = new ArrayList<>(feeds);

            RSSQuery rssQuery = new RSSQuery();
            List<FeedInsertionResult> syncErrors = new ArrayList<>();

            for (Feed feed : feedList) {
                emitter.onNext(feed);
                FeedInsertionResult syncError = new FeedInsertionResult();

                try {
                    HashMap<String, String> headers = new HashMap<>();
                    if (feed.getEtag() != null)
                        headers.put(LibUtils.IF_NONE_MATCH_HEADER, feed.getEtag());
                    if (feed.getLastModified() != null)
                        headers.put(LibUtils.IF_MODIFIED_HEADER, feed.getLastModified());

                    RSSQueryResult queryResult = rssQuery.queryUrl(feed.getUrl(), headers);
                    if (queryResult != null && queryResult.getException() == null)
                        insertNewItems(queryResult.getFeed(), queryResult.getRssType());
                    else if (queryResult != null && queryResult.getException() != null) {
                        Exception e = queryResult.getException();

                        if (e instanceof UnknownFormatException)
                            syncError.setInsertionError(FeedInsertionResult.FeedInsertionError.FORMAT_ERROR);
                        else if (e instanceof NetworkErrorException)
                            syncError.setInsertionError(FeedInsertionResult.FeedInsertionError.NETWORK_ERROR);

                        syncError.setFeed(feed);
                        syncErrors.add(syncError);
                    }
                } catch (Exception e) {
                    if (e instanceof IOException)
                        syncError.setInsertionError(FeedInsertionResult.FeedInsertionError.NETWORK_ERROR);
                    else
                        syncError.setInsertionError(FeedInsertionResult.FeedInsertionError.PARSE_ERROR);

                    syncError.setFeed(feed);
                    syncErrors.add(syncError);
                }
            }

            emitter.onComplete();
        });
    }

    @Override
    public void addFeed(ParsingResult result) {
        executor.execute(() -> {
            try {
                RSSQuery rssQuery = new RSSQuery();

                RSSQueryResult queryResult = rssQuery.queryUrl(result.getUrl(), new HashMap<>());
                if (queryResult != null && queryResult.getException() == null) {
                    insertFeed(queryResult.getFeed(), queryResult.getRssType());
                }

            } catch (Exception e) {

            }
        });
    }

    @Override
    public Single<List<FeedInsertionResult>> addFeeds(List<ParsingResult> results) {
         return Single.create(emitter -> {
             List<FeedInsertionResult> insertionResults = new ArrayList<>();

             for (ParsingResult parsingResult : results) {
                 FeedInsertionResult insertionResult = new FeedInsertionResult();

                 try {
                     RSSQuery rssNet = new RSSQuery();
                     RSSQueryResult queryResult = rssNet.queryUrl(parsingResult.getUrl(), new HashMap<>());

                     if (queryResult != null && queryResult.getException() == null) {
                        Feed feed = insertFeed(queryResult.getFeed(), queryResult.getRssType());
                        if (feed != null) {
                            insertionResult.setFeed(feed);
                            insertionResults.add(insertionResult);
                        }
                     } else if (queryResult != null && queryResult.getException() != null) {
                         insertionResult.setParsingResult(parsingResult);
                         insertionResult.setInsertionError(getErrorFromException(queryResult.getException()));

                         insertionResults.add(insertionResult);
                     } else {
                         // error 304
                     }
                 } catch (Exception e) {
                     if (e instanceof IOException)
                         insertionResult.setInsertionError(FeedInsertionResult.FeedInsertionError.NETWORK_ERROR);
                     else
                         insertionResult.setInsertionError(FeedInsertionResult.FeedInsertionError.PARSE_ERROR);

                     insertionResult.setParsingResult(parsingResult);
                     insertionResults.add(insertionResult);
                 }
             }

            emitter.onSuccess(insertionResults);
        });
    }

    @Override
    public void updateFeed(Feed feed) {
        executor.execute(() -> {
            try {
                database.feedDao().update(feed);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void updateFeedWithFolder(FeedWithFolder feedWithFolder) {
        executor.execute(() -> {
            Feed feed = feedWithFolder.getFeed();
            database.feedDao().updateFeedFields(feed.getId(), feed.getName(), feed.getUrl(), feed.getFolderId());
        });
    }

    @Override
    public Completable deleteFeed(int feedId) {
        return Completable.create(emitter -> {
            database.feedDao().delete(feedId);
            emitter.onComplete();
        });
    }

    @Override
    public Completable addFolder(Folder folder) {
        return Completable.create(emitter -> {
            database.folderDao().insert(folder);
            emitter.onComplete();
        });
    }

    private void insertNewItems(AFeed feed, RSSQuery.RSSType type) throws ParseException {
        Feed dbFeed = null;
        List<Item> items = null;

        switch (type) {
            case RSS_2:
                dbFeed = database.feedDao().getFeedByUrl(((RSSFeed) feed).getChannel().getFeedUrl());
                items = Item.itemsFromRSS(((RSSFeed) feed).getChannel().getItems(), dbFeed);
                break;
            case RSS_ATOM:
                dbFeed = database.feedDao().getFeedByUrl(((ATOMFeed) feed).getUrl());
                items = Item.itemsFromATOM(((ATOMFeed) feed).getEntries(), dbFeed);
                break;
            case RSS_JSON:
                dbFeed = database.feedDao().getFeedByUrl(((JSONFeed) feed).getFeedUrl());
                items = Item.itemsFromJSON(((JSONFeed) feed).getItems(), dbFeed);
                break;
        }

        database.feedDao().updateHeaders(dbFeed.getEtag(), dbFeed.getLastModified(), dbFeed.getId());

        Collections.sort(items, Item::compareTo);
        insertItems(items, dbFeed);
    }

    private Feed insertFeed(AFeed feed, RSSQuery.RSSType type) throws IOException {
        Feed dbFeed = null;
        switch (type) {
            case RSS_2:
                dbFeed = Feed.feedFromRSS((RSSFeed) feed);
                break;
            case RSS_ATOM:
                dbFeed = Feed.feedFromATOM((ATOMFeed) feed);
                break;
            case RSS_JSON:
                dbFeed = Feed.feedFromJSON((JSONFeed) feed);
                break;
        }

        if (Boolean.valueOf(database.feedDao().feedExists(dbFeed.getUrl())))
            return null; // feed already inserted

        setFavIconUtils(dbFeed);

        // we need empty headers to query the feed just after, without any 304 result
        dbFeed.setEtag(null);
        dbFeed.setLastModified(null);

        dbFeed.setId((int)(database.feedDao().insert(dbFeed)));
        return dbFeed;
    }

    private void insertItems(Collection<Item> items, Feed feed) {
        for (Item dbItem : items) {
            if (!Boolean.valueOf(database.itemDao().guidExist(dbItem.getGuid()))) {
                if (dbItem.getDescription() != null) {
                    dbItem.setCleanDescription(Jsoup.parse(dbItem.getDescription()).text());

                    if (dbItem.getImageLink() == null) {
                        String imageUrl = HtmlParser.getDescImageLink(dbItem.getDescription(), feed.getSiteUrl());

                        if (imageUrl != null)
                            dbItem.setImageLink(imageUrl);
                    }
                }

                // we check a second time because imageLink could have been set earlier with media:content tag value
                if (dbItem.getImageLink() != null) {
                    if (dbItem.getContent() != null) {
                        // removing cover image in content if found in description
                        dbItem.setContent(HtmlParser.deleteCoverImage(dbItem.getContent()));

                    } else if (dbItem.getDescription() != null)
                        dbItem.setDescription(HtmlParser.deleteCoverImage(dbItem.getDescription()));
                }

                if (dbItem.getContent() != null)
                    dbItem.setReadTime(Utils.readTimeFromString(Jsoup.parse(dbItem.getContent()).text()));
                else if (dbItem.getDescription() != null)
                    dbItem.setReadTime(Utils.readTimeFromString(dbItem.getCleanDescription()));

                database.itemDao().insert(dbItem);
            }
        }
    }

    private FeedInsertionResult.FeedInsertionError getErrorFromException(Exception e) {
        if (e instanceof UnknownFormatException)
            return FeedInsertionResult.FeedInsertionError.FORMAT_ERROR;
        else if (e instanceof NetworkErrorException)
            return FeedInsertionResult.FeedInsertionError.NETWORK_ERROR;
        else
            return FeedInsertionResult.FeedInsertionError.UNKNOWN_ERROR;
    }




}
package com.readrops.api.services

import com.readrops.db.entities.Feed
import com.readrops.db.entities.Folder
import com.readrops.db.entities.Item

class SyncResult {

    var items: List<Item> = mutableListOf()

    var feeds: List<Feed> = listOf()

    var folders: List<Folder> = listOf()

    var isError: Boolean = false
}

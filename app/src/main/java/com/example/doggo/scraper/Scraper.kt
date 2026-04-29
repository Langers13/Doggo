package com.example.doggo.scraper

import com.example.doggo.data.HouseSitJob

interface Scraper {
    suspend fun scrape(onJobScraped: suspend (HouseSitJob) -> Boolean)
}

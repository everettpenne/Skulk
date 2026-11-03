package com.everettpenne.skulk

import com.everettpenne.skulk.data.media.MediaExtractor
import com.everettpenne.skulk.data.media.Posts
import com.everettpenne.skulk.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MediaExtractorTest {
    private fun post(json: String) = Posts.from(RedditJson.decodeFromString<Thing<PostDto>>(json).data)

    @Test fun anImagePostKeepsItsFieldsAndFindsTheFullSizeImage() {
        val p = post(Fixtures.imagePost)
        assertEquals("A & B", p.title); assertEquals("t3_abc123", p.fullname); assertEquals("r/pics", p.subreddit.displayNamePrefixed)
        assertEquals("https://styles.redditmedia.com/icon.png", p.subreddit.iconUrl)
        assertTrue(p.subreddit.isSubscribed); assertEquals(0xFF0079D3.toInt(), p.subreddit.keyColor)
        assertEquals("Photo", p.linkFlairText); assertEquals(0xFFFF4500.toInt(), p.flairBackground); assertTrue(p.flairTextLight)
        val image = (p.media as MediaType.Image).image
        assertEquals("https://i.redd.it/xyz.jpg?width=640&s=sig", image.url.replace("/preview.", "/i."))
        assertEquals(640, image.width); assertEquals(480, image.height)
    }

    @Test fun aSelfPostHasNoMedia() {
        val p = post(Fixtures.selfPost)
        assertEquals(MediaType.None, p.media); assertEquals("Why **is** the sky blue?", p.selftext)
    }

    @Test fun aGalleryUsesTheOriginalsAndSkipsFailedItems() {
        val gallery = post(Fixtures.galleryPost).media as MediaType.Gallery
        assertEquals(listOf("https://i.redd.it/m1.jpg", "https://i.redd.it/m3.png"), gallery.images.map { it.url })
        assertEquals(800, gallery.images[0].width)
        assertEquals(.5f, gallery.images[1].aspectRatio!!, 1e-6f)
    }

    @Test fun aRedditVideoUsesItsStreamAndTheNonExternalThumbnailOnly() {
        val v = post(Fixtures.videoPost).media as MediaType.Video
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", v.url); assertEquals(720, v.width); assertEquals(1280, v.height)
        assertNull("external-preview thumbnails are skipped", v.thumbnailUrl)
    }

    @Test fun aYouTubeLinkIsRecognisedWithItsThumbnail() {
        val y = post(Fixtures.youtubePost).media as MediaType.YouTube
        assertEquals("dQw4w9WgXcQ", y.videoId); assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", y.image.url)
    }

    @Test fun anOrdinaryLinkBecomesALinkCard() {
        val l = (post(Fixtures.linkPost).media as MediaType.Link).metadata
        assertEquals("https://example.com/story", l.url); assertEquals("example.com", l.domain)
        assertEquals("https://external-preview.redd.it/t.jpg", l.thumbnailUrl)
    }

    @Test fun aCrosspostShowsTheOriginal() {
        val r = post(Fixtures.crosspost).media as MediaType.Repost
        assertEquals("Original", r.original.title); assertTrue(r.original.media is MediaType.Image)
    }

    @Test fun youTubeIdsComeFromEveryUrlShape() {
        assertEquals("abc", MediaExtractor.youTubeId("https://youtu.be/abc?t=3"))
        assertEquals("abc", MediaExtractor.youTubeId("https://www.youtube.com/watch?v=abc&x=1"))
        assertEquals("abc", MediaExtractor.youTubeId("https://www.youtube.com/embed/abc"))
        assertNull(MediaExtractor.youTubeId("https://example.com"))
    }

    @Test fun missingFieldsNeverBreakAPost() {
        val p = post("""{"kind":"t3","data":{"id":"x"}}""")
        assertEquals("x", p.id); assertEquals("t3_x", p.fullname); assertEquals(MediaType.None, p.media)
    }
}

class SavedCodecTest {
    private fun post(json: String) = Posts.from(RedditJson.decodeFromString<Thing<PostDto>>(json).data)

    @Test fun savedPostsSurviveAWriteAndReadWithEveryKindOfMedia() {
        val posts = listOf(Fixtures.imagePost, Fixtures.selfPost, Fixtures.galleryPost, Fixtures.videoPost, Fixtures.youtubePost, Fixtures.linkPost, Fixtures.crosspost).map(::post)
        assertEquals(posts, com.everettpenne.skulk.data.SavedCodec.decode(com.everettpenne.skulk.data.SavedCodec.encode(posts)))
    }

    @Test fun unreadableStorageIsAnEmptyList() {
        assertEquals(emptyList<Post>(), com.everettpenne.skulk.data.SavedCodec.decode("garbage"))
        assertEquals(emptyList<Post>(), com.everettpenne.skulk.data.SavedCodec.decode(null))
    }

    private fun gifPost(url: String, domain: String, preview: String = "") = """
    {"kind":"t3","data":{"id":"g1","name":"t3_g1","title":"Gif","author":"ivy","subreddit":"gifs","ups":1,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/gifs/comments/g1/gif/","url":"$url","domain":"$domain","is_self":false$preview}}
    """.trimIndent()

    @Test fun anAnimationPlaysAsALoopingVideoWhenRedditHasAnMp4OfIt() {
        val preview = ""","preview":{"images":[{"source":{"url":"https://preview.redd.it/a.gif?s=1","width":400,"height":300},
            "variants":{"mp4":{"source":{"url":"https://preview.redd.it/a.gif?format=mp4&amp;s=2","width":400,"height":300}}}}]}"""
        val v = post(gifPost("https://i.redd.it/a.gif", "i.redd.it", preview)).media as MediaType.Video
        assertEquals("https://preview.redd.it/a.gif?format=mp4&s=2", v.url); assertTrue(v.loops); assertEquals(400, v.width)
    }

    @Test fun anImgurGifvPlaysItsMp4Twin() {
        val v = post(gifPost("https://i.imgur.com/abc.gifv", "i.imgur.com")).media as MediaType.Video
        assertEquals("https://i.imgur.com/abc.mp4", v.url); assertTrue(v.loops)
    }
}

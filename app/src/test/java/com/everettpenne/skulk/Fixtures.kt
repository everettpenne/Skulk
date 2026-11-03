package com.everettpenne.skulk

/** Reddit-shaped JSON, trimmed to the fields the app reads but keeping Reddit's quirks (empty-string replies, HTML-escaped URLs, a failed gallery item). */
object Fixtures {
    val imagePost = """
    {"kind":"t3","data":{"id":"abc123","name":"t3_abc123","title":"A &amp; B","author":"alice","subreddit":"pics","ups":1234,"downs":0,
     "num_comments":56,"created_utc":1700000000.0,"permalink":"/r/pics/comments/abc123/a_b/","url":"https://i.redd.it/xyz.jpg","domain":"i.redd.it",
     "is_self":false,"selftext":"","saved":false,"likes":null,"over_18":false,"thumbnail":"https://b.thumbs.redditmedia.com/t.jpg",
     "link_flair_text":"Photo","link_flair_text_color":"light","link_flair_background_color":"#FF4500",
     "preview":{"images":[{"source":{"url":"https://preview.redd.it/xyz.jpg?width=640&amp;s=sig","width":640,"height":480}}]},
     "sr_detail":{"id":"2qh0u","display_name":"pics","display_name_prefixed":"r/pics","community_icon":"https://styles.redditmedia.com/icon.png?width=256&amp;s=1",
       "key_color":"#0079d3","subscribers":30000000,"user_is_subscriber":true,"public_description":"Pictures"}}}
    """.trimIndent()

    val selfPost = """
    {"kind":"t3","data":{"id":"s1","name":"t3_s1","title":"Question","author":"bob","subreddit":"askreddit","ups":5,"num_comments":2,"created_utc":1700000000,
     "permalink":"/r/askreddit/comments/s1/question/","url":"https://www.reddit.com/r/askreddit/comments/s1/question/","domain":"self.askreddit","is_self":true,
     "selftext":"Why **is** the sky blue?"}}
    """.trimIndent()

    val galleryPost = """
    {"kind":"t3","data":{"id":"g1","name":"t3_g1","title":"Gallery","author":"carol","subreddit":"art","ups":9,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/art/comments/g1/gallery/","url":"https://www.reddit.com/gallery/g1","domain":"reddit.com","is_self":false,"is_gallery":true,
     "gallery_data":{"items":[{"media_id":"m1"},{"media_id":"m2"},{"media_id":"m3"}]},
     "media_metadata":{"m1":{"m":"image/jpg","s":{"x":800,"y":600}},"m2":{"status":"failed"},"m3":{"m":"image/png","s":{"x":100,"y":200}}}}}
    """.trimIndent()

    val videoPost = """
    {"kind":"t3","data":{"id":"v1","name":"t3_v1","title":"Clip","author":"dan","subreddit":"videos","ups":9,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/videos/comments/v1/clip/","url":"https://v.redd.it/abc","domain":"v.redd.it","is_self":false,
     "media":{"reddit_video":{"hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8","width":720,"height":1280}},
     "preview":{"images":[{"source":{"url":"https://external-preview.redd.it/x.jpg?s=1","width":720,"height":1280}}]}}}
    """.trimIndent()

    val youtubePost = """
    {"kind":"t3","data":{"id":"y1","name":"t3_y1","title":"Tube","author":"eve","subreddit":"music","ups":9,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/music/comments/y1/tube/","url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10","domain":"youtube.com","is_self":false}}
    """.trimIndent()

    val linkPost = """
    {"kind":"t3","data":{"id":"l1","name":"t3_l1","title":"News","author":"fay","subreddit":"news","ups":9,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/news/comments/l1/news/","url":"https://example.com/story","domain":"example.com","is_self":false,"thumbnail":"https://external-preview.redd.it/t.jpg"}}
    """.trimIndent()

    val crosspost = """
    {"kind":"t3","data":{"id":"c1","name":"t3_c1","title":"Cross","author":"gus","subreddit":"funny","ups":9,"num_comments":0,"created_utc":1700000000,
     "permalink":"/r/funny/comments/c1/cross/","url":"https://i.redd.it/o.jpg","domain":"i.redd.it","is_self":false,
     "crosspost_parent_list":[{"id":"o1","name":"t3_o1","title":"Original","author":"hal","subreddit":"pics","ups":3,"num_comments":0,"created_utc":1700000000,
        "permalink":"/r/pics/comments/o1/original/","url":"https://i.redd.it/o.jpg","domain":"i.redd.it","is_self":false}]}}
    """.trimIndent()

    fun listing(vararg things: String, after: String? = null) =
        """{"kind":"Listing","data":{"after":${if (after == null) "null" else "\"$after\""},"children":[${things.joinToString(",")}]}}"""

    /** [post listing, comment listing]: a thread with a nested reply, an empty-string replies field, an image comment and a "more" stub. */
    val commentsResponse = """
    [${listing(imagePost)},
     {"kind":"Listing","data":{"after":null,"children":[
      {"kind":"t1","data":{"id":"c_a","author":"top","body":"Top level &amp; bold","created_utc":1700000100,"score":10,"ups":10,"depth":0,"parent_id":"t3_abc123",
        "likes":true,"is_submitter":true,"replies":{"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t1","data":{"id":"c_b","author":"mid","body":"A reply","created_utc":1700000200,"score":4,"ups":4,"depth":1,"parent_id":"t1_c_a",
            "replies":{"kind":"Listing","data":{"after":null,"children":[
              {"kind":"t1","data":{"id":"c_c","author":"deep","body":"Deepest","created_utc":1700000300,"score":1,"ups":1,"depth":2,"parent_id":"t1_c_b","replies":""}}]}}}}]}}}},
      {"kind":"t1","data":{"id":"c_d","author":"second","body":"![img](m9) look","created_utc":1700000400,"score":2,"ups":2,"depth":0,"parent_id":"t3_abc123","replies":"",
        "media_metadata":{"m9":{"m":"image/png","s":{"u":"https://preview.redd.it/pic.png?width=216&amp;s=x","x":216,"y":100}}}}},
      {"kind":"more","data":{"count":3,"children":["zz1","zz2"]}}]}}]
    """.trimIndent()
}

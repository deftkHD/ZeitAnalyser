import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

private val newspapers = listOf("2019/49", "2019/51")

private const val metaLink = "http://xml.zeit.de/"
private val gson = GsonBuilder().setPrettyPrinting().create()

fun main() {
    // fetch articles
    val articles = mutableListOf<ArticleObject>()
    newspapers.forEach { newspaper ->
        fetchArticles(newspaper).forEach { article ->
            if (articles.contains(article)) {
                articles[articles.indexOf(article)].ressorts.addAll(article.ressorts) // hopefully no duplicates here
            } else {
                articles.add(article)
            }
        }
    }
    println("Done fetching articles")

    // compare authors
    val authors = mutableListOf<String>()
    val authorsTwice = mutableListOf<String>()
    articles.forEach { article ->
        article.authors.forEach { author ->
            if (authors.contains(author) && !authorsTwice.contains(author)) {
                authorsTwice.add(author)
            } else {
                authors.add(author)
            }
        }
    }
    println("Done comparing authors (found ${authorsTwice.size} authors twice or more)")

    // generate json output
    val root = JsonObject()
    val jAuthors = JsonArray()
    authorsTwice.forEach { author ->
        val authorsArticles = articles.filter { it.authors.contains(author) }
        val articlesObject = JsonArray()
        authorsArticles.forEach { article ->
            val articleObject = JsonObject()
            articleObject.addProperty(
                "link",
                article.metaLink.replaceFirst("xml.", "").replaceFirst("http://", "https://")
            )
            val ressortsArray = JsonArray()
            article.ressorts.forEach { ressort ->
                ressortsArray.add(ressort)
            }
            articleObject.add("ressorts", ressortsArray)
            articleObject.addProperty("subRessort", article.subRessort)
            articleObject.addProperty("contentType", article.contentType)
            articleObject.addProperty("superTitle", article.superTitle)
            articleObject.addProperty("title", article.title)
            if (article.genre != null)
                articleObject.addProperty("genre", article.genre)
            articlesObject.add(articleObject)
        }

        val authorObject = JsonObject()
        authorObject.addProperty("name", author)
        authorObject.add("articles", articlesObject)
        jAuthors.add(authorObject)
    }
    root.add("authors", jAuthors)

    println(gson.toJson(root))
}

private fun fetchArticles(newspaper: String): List<ArticleObject> {
    val articles = mutableListOf<ArticleObject>()
    val response = performGETRequest(URL("$metaLink$newspaper/index"))

    // parse response
    val doc = Jsoup.parse(response)
    val ressorts = doc.getElementsByTag("region").filter { it.hasAttr("title") && it.childrenSize() > 0 }
    ressorts.forEach { ressort ->
        val ressortName = ressort.attr("title")
        ressort.getElementsByTag("container").forEach { article ->
            val blocks = article.getElementsByTag("block")
            if (blocks.size > 1) {
                println("Warning: Article with more than one block: $article (Only the first block will be interpreted)")
            } else if (blocks.isEmpty()) {
                println("Error: Article without any block: $article (Program will crash)")
            }
            articles.add(
                ArticleObject(
                    blocks[0].attr("href"),
                    mutableListOf(ressortName),
                    blocks[0].attr("ressort"),
                    blocks[0].attr("contenttype"),
                    blocks[0].getElementsByTag("supertitle")[0].text(),
                    blocks[0].getElementsByTag("title")[0].text(),
                    if (blocks[0].hasAttr("genre")) blocks[0].attr("genre") else null,
                    blocks[0].attr("author").split(";")
                )
            )
        }
    }
    return articles
}

private fun performGETRequest(url: URL): String {
    val connection = url.openConnection() as HttpURLConnection
    val data = connection.inputStream.readBytes()
    return String(data, Charsets.ISO_8859_1)
}

data class ArticleObject(
    val metaLink: String,
    val ressorts: MutableList<String>,
    val subRessort: String,
    val contentType: String,
    val superTitle: String,
    val title: String,
    val genre: String?,
    val authors: List<String>
) {

    override fun hashCode(): Int {
        return metaLink.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArticleObject
        return (metaLink == other.metaLink)
    }

}
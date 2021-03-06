package red.medusa.markdownpanel.view

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import red.medusa.markdownpanel.InlineText
import red.medusa.markdownpanel.MKData
import red.medusa.markdownpanel.pt
import red.medusa.markdownpanel.view.Styles.Companion.WordHighlight
import tornadofx.*
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream


enum class DoubtfulType {
    LINK, IMAGE, TEXT, BOLD, ITALICS, BOLD_ITALICS, LABEL_WORD
}

data class TagModel(
    var range: IntRange,
    var name: String? = null,
    var url: String? = null,
    var type: DoubtfulType = DoubtfulType.TEXT
)

open class Segment : Pane() {
    companion object {
        // 图片
        val imageRegex = Regex("""!\[(alt(.*))?]\(((?:.(?!"))+?)(\s*\s"(.*?)"\s*)?\)""")
        // 链接
        val linkRegex = """\[(?!alt)([^]]*)]\(([^)]+)\)""".toRegex()
        // 链接<.+>
        val linkRegex2 = """<(.+)>""".toRegex()
        // 斜体加粗
        val boldItalicsRegex = """(?:\*\*\*(.*)\*\*\*)|(?:___(.*)___)""".toRegex()
        // 加粗
        val boldRegex = """(?:\*\*(.*)\*\*)|(?:__(.*)__)""".toRegex()
        // 斜体
        val italicsRegex = """(?:\*(.*)\*)|(?:_(.*)_)""".toRegex()
        // 单词高亮
        val labelWordRegex = """`([^`]+)`""".toRegex()
    }

    open fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText? = null
    ): Pane? = vbox {
        prefix?.apply {
            label("$this ")
        }
        label {
            text = str
        }
    }

    fun getSegmentForInlineBlock(
        str: String,
        css: CssRule,
        prefix: String?
    ): Pane? = hbox {
        hgrow = Priority.ALWAYS
        addClass(css)
        prefix?.apply {
            label("$this ")
        }
        this.add(str.getInlineBlock())
    }

    private fun createTagModels(text: String, regex: Regex, transform: (MatchResult) -> TagModel): LinkedList<Any> {
        val list = LinkedList<Any>()
        val rs = LinkedList<Any>()
        var result = regex.find(text)
        while (result != null) {
            rs.add(transform(result))
            result = result.next()
        }
        var start = 0
        rs.forEach {
            if (it is TagModel) {
                list.add(text.substring(start, it.range.start))
                list.add(it)
                start = it.range.endInclusive + 1
            }
        }
        list.add(text.substring(start))
        return list
    }

    private fun Collection<Any>.flatMap(): LinkedList<Any> = this.stream().flatMap {
        when (it) {
            is Collection<*> ->
                it.stream()
            else ->
                Stream.of(it)
        }
    }.collect(Collectors.toCollection(::LinkedList))

    private val nestListLengthTimes = 4
    fun String.padStarts(c: Char) =
        when {
            this.length < nestListLengthTimes -> this.padStart(nestListLengthTimes, c)
            this.length % nestListLengthTimes != 0 -> {
                val length = this.length + nestListLengthTimes - (this.length % nestListLengthTimes)
                this.padStart(length, c)
            }
            else -> this
        }

    fun String.needTimes(minimum: Int) =
        when {
            this.length <= nestListLengthTimes -> minimum
            this.length % nestListLengthTimes != 0 -> {
                (this.length + nestListLengthTimes - (this.length % nestListLengthTimes)) / nestListLengthTimes
            }
            else -> this.length / nestListLengthTimes
        }

    fun ImageView.getImage(url: String) {
        try {
            if (url.trimStart().startsWith("http")) {
                runAsync {
                    val image = URL(url).openStream()
                    this@getImage.image = Image(image)
                }
            } else {
                MKData.mkFile?.resolve(File(url))?.canonicalFile?.apply {
                    if (this.exists() && this.isFile) {
                        this@getImage.image = Image(FileInputStream(this))
                    }
                }
            }
        } catch (e: Exception) {
            this.image = Image("data/markdown-file/img/load_error.jpg")
            this.tooltip("找不到图片")
        }
    }


    fun String.debug() {
        pt("Segment ${this@Segment.javaClass.simpleName} is loading ===>$this")
    }

    /**
     * 内联样式
     */
    fun String.getInlineBlock() = flowpane {
        hgrow = Priority.ALWAYS
        alignment = Pos.BASELINE_LEFT
        val contents = this@getInlineBlock.parseMe()
        contents?.forEach { node ->
            if (node is String) {
                label(node) {
                    style {
                        padding = box(0.px)
                    }
                }
            } else if (node is TagModel) {
                val item = getNode(node)
                with(item) {
                    this!!.style {
                        padding = box(0.px)
                    }
                }
                item?.attachTo(this)
            }
        }
    }

    private fun getNode(node: TagModel): Node? {
        val title = node.name ?: ""
        return when (node.type) {
            DoubtfulType.TEXT -> return label(title)
            DoubtfulType.LABEL_WORD -> return label(title) { addClass(Styles.WordHighlight) }
            DoubtfulType.BOLD_ITALICS -> return label(title) { addClass(Styles.boldItalics) }
            DoubtfulType.BOLD -> return label(title) {
                addClass(Styles.bold)
            }
            DoubtfulType.ITALICS -> return label(title) {
                addClass(Styles.italics)
            }
            DoubtfulType.LINK -> {
                return hyperlink(node.name ?: node.url ?: "") {
                    tooltip(node.url ?: "")
                    onDoubleClick {
                        FX.eventbus.fire(OpenWindowForHyperlink(node.url ?: ""))
                    }
                }
            }
            DoubtfulType.IMAGE -> ImageView().apply { getImage(node.url!!) }
        }
    }

    private fun String.parseMe(): LinkedList<Any>? {
        if (this.isEmpty() || this.isBlank())
            return null
        var tagModels = LinkedList<Any>()
        tagModels.add(this)
        if (this.containsImage()) tagModels = parseForImage(tagModels)
        if (this.containsLink1()) tagModels = parseForLink(tagModels)
        if (this.containsLink2()) tagModels = parseForLink2(tagModels)
        if (this.containsBoldWithItalics()) tagModels = parseForBoldAndItalics(tagModels)
        if (this.containsBold()) tagModels = parseForBold(tagModels)
        if (this.containsItalics()) tagModels = parseForItalics(tagModels)
        if (this.containsWordHighlight()) tagModels = parseForWordHighlight(tagModels)
        return tagModels
    }

    fun parseMe2(text: String): LinkedList<Any> {
        var tagModels = LinkedList<Any>()
        tagModels.add(text)
        if (text.containsImage()) tagModels = parseForImage(tagModels)
        if (text.containsLink1()) tagModels = parseForLink(tagModels)
        if (text.containsLink2()) tagModels = parseForLink2(tagModels)
        if (text.containsBoldWithItalics()) tagModels = parseForBoldAndItalics(tagModels)
        if (text.containsBold()) tagModels = parseForBold(tagModels)
        if (text.containsItalics()) tagModels = parseForItalics(tagModels)
        if (text.containsWordHighlight()) tagModels = parseForWordHighlight(tagModels)
        return tagModels
    }

    private fun String.containsWordHighlight() = contains(labelWordRegex)
    private fun String.containsImage() = contains(imageRegex)
    private fun String.containsLink1() = contains(linkRegex)
    private fun String.containsLink2() = contains(linkRegex2)
    private fun String.containsItalics() = contains(italicsRegex)
    private fun String.containsBold() = contains(boldRegex)
    private fun String.containsBoldWithItalics() = contains(boldItalicsRegex)

    fun parseForImage(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithLinks = createTagModels(
                    strWithNode,
                    imageRegex
                ) {
                    TagModel(
                        it.range,
                        it.destructured.component2(),
                        it.destructured.component3(),
                        DoubtfulType.IMAGE
                    )
                }
                contents[i] = stringWithLinks
            }
        }
        return contents.flatMap()
    }

    fun parseForLink(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithImage ->
            if (strWithImage is String) {
                val stringWithLinks = createTagModels(
                    strWithImage,
                    linkRegex
                ) {
                    TagModel(
                        it.range,
                        it.destructured.component1(),
                        it.destructured.component2(),
                        DoubtfulType.LINK
                    )
                }
                contents[i] = stringWithLinks
            }
        }
        return contents.flatMap()
    }

    fun parseForLink2(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithLinks = createTagModels(
                    strWithNode,
                    linkRegex2
                ) {
                    TagModel(
                        it.range,
                        null,
                        it.destructured.component1(),
                        DoubtfulType.LINK
                    )
                }
                contents[i] = stringWithLinks
            }
        }
        return contents.flatMap()
    }

    fun parseForBoldAndItalics(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithNodes = createTagModels(
                    strWithNode,
                    boldItalicsRegex
                ) {
                    var title = it.destructured.component1()
                    if (title.isEmpty())
                        title = it.destructured.component2()
                    TagModel(
                        it.range,
                        title,
                        null,
                        DoubtfulType.BOLD_ITALICS
                    )
                }
                contents[i] = stringWithNodes
            }
        }
        return contents.flatMap()
    }

    fun parseForWordHighlight(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithNodes = createTagModels(strWithNode, labelWordRegex) {
                    var title = it.destructured.component1()
                    TagModel(it.range, title, null, DoubtfulType.LABEL_WORD)
                }
                contents[i] = stringWithNodes
            }
        }
        return contents.flatMap()
    }

    fun parseForBold(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithNodes = createTagModels(
                    strWithNode,
                    boldRegex
                ) {
                    var title = it.destructured.component1()
                    if (title.isEmpty())
                        title = it.destructured.component2()
                    TagModel(
                        it.range,
                        title,
                        null,
                        DoubtfulType.BOLD
                    )
                }
                contents[i] = stringWithNodes
            }
        }
        return contents.flatMap()
    }

    fun parseForItalics(contents: MutableList<Any>): LinkedList<Any> {
        contents.forEachIndexed { i, strWithNode ->
            if (strWithNode is String) {
                val stringWithNodes = createTagModels(
                    strWithNode,
                    italicsRegex
                ) {
                    var title = it.destructured.component1()
                    if (title.isEmpty())
                        title = it.destructured.component2()
                    TagModel(
                        it.range,
                        title,
                        null,
                        DoubtfulType.ITALICS
                    )
                }
                contents[i] = stringWithNodes
            }
        }
        return contents.flatMap()
    }
}

class OneTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.OneTitleSegment, prefix)
    }
}

class TowTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.TowTitleSegment, prefix)
    }
}

class ThreeTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.ThreeTitleSegment, prefix)
    }
}

class FourTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.FourTitleSegment, prefix)
    }
}

class FiveTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.FiveTitleSegment, prefix)
    }
}

class SixTitleSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegmentForInlineBlock(str, Styles.SixTitleSegment, prefix)
    }
}

class BlankSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return HBox()
    }
}

class LinefeedSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return super.getSegment(str, prefix, postfix, lineNumber, inlineText)
    }
}

class SeparatorSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return vbox {
            addClass(Styles.SeparatorSegment)
            separator {
                useMaxSize = true
            }
        }
    }
}

class UnOrderedSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {
            addClass(Styles.paddingTow)
            label("▪ ") {
                addClass(Styles.bold)
            }
            this.add(str.getInlineBlock())
        }
    }
}

class OrderedSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {
            addClass(Styles.paddingTow)
            prefix?.apply {
                label(prefix) {
                    addClass(Styles.bold)
                }
            }
            this.add(str.getInlineBlock())
        }
    }
}

class NestUnOrderedSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {
            addClass(Styles.ListPadding)
            val times = (prefix?.needTimes(1) ?: 1) * 2 + 2
            style {
                padding = box(0.em, 0.em, 0.em, times.em)
            }
            label("▪") {
                addClass(Styles.bold)
            }
            this.add(str.getInlineBlock())
        }
    }
}

class NestOrderedSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {
            addClass(Styles.ListPadding)
            val times = (prefix?.needTimes(1) ?: 1) * 2 + 2
            style {
                padding = box(0.em, 0.em, 0.em, times.em)
            }
            postfix?.apply {
                label(postfix) {
                    addClass(Styles.bold)
                }
            }
            this.add(str.getInlineBlock())
        }
    }
}


class OpenWindowForHyperlink(val url: String) : FXEvent()

class ParagraphSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return str.getInlineBlock().apply {
            alignment = Pos.BASELINE_LEFT
            addClass(Styles.ParagraphSegment)
        }

    }

}

class LabelWordHighlightSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane? {
        str.debug()
        return vbox {
            label(str).apply {
                addClass(WordHighlight)
            }
        }

    }

}

class CodeArea1Segment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return vbox {
            addClass(Styles.CoderArea)
            text(str) {
                addClass(Styles.CoderAreaColor)
            }
        }
    }
}

class CodeArea2Segment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()

        return vbox {
            addClass(Styles.CoderArea)
            text(str) {
                addClass(Styles.CoderAreaColor)
            }
        }
    }
}


class LinkSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {

            inlineText?.apply {
                val url = inlineText.linkName ?: inlineText.linkUrl ?: ""
                hyperlink(url) {
                    tooltip(inlineText.linkUrl ?: "")
                    onDoubleClick {
                        FX.eventbus.fire(OpenWindowForHyperlink(inlineText.linkUrl ?: ""))
                    }
                }
            }
        }
    }
}

class ImageSegment : Segment() {
    override fun getSegment(
        str: String,
        prefix: String?,
        postfix: String?,
        lineNumber: Int?,
        inlineText: InlineText?
    ): Pane {
        str.debug()
        return hbox {
            inlineText?.apply {
                if (inlineText.imageUrl != null) {
                    this@hbox.add(ImageView().apply { this.getImage(inlineText.imageUrl!!) })
                }
            }
        }
    }
}



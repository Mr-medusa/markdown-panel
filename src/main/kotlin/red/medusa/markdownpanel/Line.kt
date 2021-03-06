package red.medusa.markdownpanel

import red.medusa.markdownpanel.view.ParagraphSegment
import red.medusa.markdownpanel.view.Segment
import kotlin.reflect.KClass

data class InlineText(
    var linkName: String? = null,
    var linkUrl: String? = null,
    var imageName: String? = null,
    var imageUrl: String? = null
)

data class Line(
    val number: Int,
    var text: String,
    var inlineText: InlineText? = null,
    var prefix: String? = null,
    var postfix: String? = null,
    var tagParse: TagParse = TagParse.TagType.PARAGRAPH,         // 默认解析成段落
    var segmentView: KClass<out Segment> = ParagraphSegment::class,        // 解析成段落的具体实现类
    var isLine: Boolean = true
)
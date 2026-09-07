package com.example.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DynamicPageBlock
import com.example.data.DynamicTab

private data class CourseLesson(
    val id: String,
    val title: String,
    val description: String = "",
    val blocks: List<DynamicPageBlock>
)

private data class CourseModule(
    val id: String,
    val title: String,
    val lessons: List<CourseLesson>
)

private data class MutableCourseLesson(
    val id: String,
    val title: String,
    val description: String,
    val blocks: MutableList<DynamicPageBlock> = mutableListOf()
)

private data class MutableCourseModule(
    val id: String,
    val title: String,
    val lessons: MutableList<MutableCourseLesson> = mutableListOf()
)

@Composable
fun DynamicCourseContent(
    tab: DynamicTab,
    blocks: List<DynamicPageBlock>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val modules = remember(blocks) { parseCourseStructure(blocks) }
    val allLessons = remember(modules) { modules.flatMap { it.lessons } }
    val progressKey = remember(tab.id) { "course_${tab.id}_completed" }
    val prefs = remember(tab.id) {
        context.getSharedPreferences("nrd_course_progress", Context.MODE_PRIVATE)
    }

    var completedIds by remember(tab.id, allLessons) {
        mutableStateOf(
            prefs.getStringSet(progressKey, emptySet())
                ?.filterTo(mutableSetOf()) { savedId -> allLessons.any { it.id == savedId } }
                .orEmpty()
        )
    }

    val firstIncomplete = allLessons.firstOrNull { it.id !in completedIds }
    var expandedLessonId by remember(tab.id, allLessons) {
        mutableStateOf(firstIncomplete?.id ?: allLessons.firstOrNull()?.id)
    }
    var expandedModuleIds by remember(tab.id, modules) {
        mutableStateOf(
            modules.firstOrNull()?.let { setOf(it.id) }.orEmpty()
        )
    }

    val progress = if (allLessons.isEmpty()) 0f
    else (completedIds.size.toFloat() / allLessons.size.toFloat()).coerceIn(0f, 1f)

    fun persistProgress(next: Set<String>) {
        completedIds = next
        prefs.edit().putStringSet(progressKey, next).apply()
    }

    fun openLesson(lessonId: String) {
        expandedLessonId = lessonId
        modules.firstOrNull { module -> module.lessons.any { it.id == lessonId } }?.let { module ->
            expandedModuleIds = expandedModuleIds + module.id
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "TREINAMENTO NRD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        "Curso disponibilizado pelo Painel Mestre. O acesso é direto, sem login ou matrícula.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                    )
                    Text(
                        if (allLessons.isEmpty()) "Nenhuma aula disponível"
                        else "${completedIds.size} de ${allLessons.size} aula(s) concluída(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            label = { Text("${modules.size} módulo(s)") },
                            leadingIcon = { Icon(Icons.Default.ViewModule, contentDescription = null) }
                        )
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            label = { Text("${allLessons.size} aula(s)") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                        )
                    }

                    if (firstIncomplete != null) {
                        Button(
                            onClick = { openLesson(firstIncomplete.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (completedIds.isEmpty()) "Começar curso" else "Continuar curso")
                        }
                    } else if (allLessons.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                persistProgress(emptySet())
                                allLessons.firstOrNull()?.id?.let(::openLesson)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Rever curso")
                        }
                    }
                }
            }
        }

        if (modules.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Nenhuma aula foi adicionada a este curso.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(modules, key = { it.id }) { module ->
            val moduleExpanded = module.id in expandedModuleIds
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedModuleIds = if (moduleExpanded) {
                                    expandedModuleIds - module.id
                                } else {
                                    expandedModuleIds + module.id
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ViewModule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                module.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val doneInModule = module.lessons.count { it.id in completedIds }
                            Text(
                                "$doneInModule de ${module.lessons.size} aula(s) concluída(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (moduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (moduleExpanded) "Recolher módulo" else "Abrir módulo"
                        )
                    }

                    if (moduleExpanded) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            module.lessons.forEachIndexed { index, lesson ->
                                val lessonExpanded = expandedLessonId == lesson.id
                                val completed = lesson.id in completedIds
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (lessonExpanded)
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedLessonId = if (lessonExpanded) null else lesson.id
                                                }
                                                .padding(horizontal = 12.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (completed) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = if (completed)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Aula ${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    lesson.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (lesson.description.isNotBlank()) {
                                                    Text(
                                                        lesson.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = if (lessonExpanded) 6 else 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Icon(
                                                if (lessonExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (lessonExpanded) "Recolher aula" else "Abrir aula"
                                            )
                                        }

                                        if (lessonExpanded) {
                                            HorizontalDivider()
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                lesson.blocks.forEach { block ->
                                                    CourseLessonBlock(tab = tab, block = block)
                                                }

                                                FilledTonalButton(
                                                    onClick = {
                                                        val next = if (completed) {
                                                            completedIds - lesson.id
                                                        } else {
                                                            completedIds + lesson.id
                                                        }
                                                        persistProgress(next)
                                                        if (!completed) {
                                                            val nextLesson = allLessons
                                                                .dropWhile { it.id != lesson.id }
                                                                .drop(1)
                                                                .firstOrNull { it.id !in next }
                                                            nextLesson?.let { openLesson(it.id) }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        if (completed) Icons.Default.Undo else Icons.Default.Check,
                                                        contentDescription = null
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        if (completed) "Marcar como não concluída"
                                                        else "Concluir aula"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseLessonBlock(tab: DynamicTab, block: DynamicPageBlock) {
    when (block.type) {
        DynamicPageBlock.TYPE_TEXT -> {
            val body = block.value.lineSequence().drop(1).joinToString("\n").trim()
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        DynamicPageBlock.TYPE_IMAGE -> DynamicImageBlock(
            url = block.value,
            title = tab.title
        )
        DynamicPageBlock.TYPE_VIDEO -> DynamicVideoBlock(
            url = block.value,
            title = tab.title
        )
        DynamicPageBlock.TYPE_AUDIO -> DynamicAudioBlock(
            url = block.value,
            title = tab.title
        )
        DynamicPageBlock.TYPE_PDF -> DynamicPdfBlock(
            url = block.value,
            title = tab.title
        )
        else -> Text(
            "Tipo de conteúdo não suportado.",
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun parseCourseStructure(blocks: List<DynamicPageBlock>): List<CourseModule> {
    if (blocks.isEmpty()) return emptyList()

    val modules = mutableListOf<MutableCourseModule>()
    var currentModule: MutableCourseModule? = null
    var currentLesson: MutableCourseLesson? = null
    var generatedModuleIndex = 1
    var generatedLessonIndex = 1

    fun ensureModule(): MutableCourseModule {
        return currentModule ?: MutableCourseModule(
            id = "module_auto_${generatedModuleIndex}",
            title = "Módulo ${generatedModuleIndex}"
        ).also {
            generatedModuleIndex++
            modules += it
            currentModule = it
        }
    }

    fun newLesson(
        id: String,
        title: String,
        description: String = "",
        firstBlock: DynamicPageBlock? = null
    ): MutableCourseLesson {
        val lesson = MutableCourseLesson(
            id = id,
            title = title.ifBlank { "Aula ${generatedLessonIndex}" },
            description = description
        )
        generatedLessonIndex++
        firstBlock?.let { lesson.blocks += it }
        ensureModule().lessons += lesson
        currentLesson = lesson
        return lesson
    }

    blocks.forEach { block ->
        if (block.type == DynamicPageBlock.TYPE_TEXT) {
            val lines = block.value.lines()
            val firstLine = lines.firstOrNull()?.trim().orEmpty()
            val moduleTitle = moduleMarkerTitle(firstLine)
            if (moduleTitle != null) {
                val module = MutableCourseModule(
                    id = "module_${block.id}",
                    title = moduleTitle.ifBlank { "Módulo ${modules.size + 1}" }
                )
                modules += module
                currentModule = module
                currentLesson = null
                return@forEach
            }

            val lessonTitle = firstLine.ifBlank { "Aula ${generatedLessonIndex}" }
            val description = lines.drop(1).joinToString("\n").trim()
            newLesson(
                id = "lesson_${block.id}",
                title = lessonTitle,
                description = description,
                firstBlock = block
            )
            return@forEach
        }

        val existingLesson = currentLesson
        if (existingLesson != null) {
            existingLesson.blocks += block
        } else {
            newLesson(
                id = "lesson_${block.id}",
                title = labelForCourseBlock(block.type),
                firstBlock = block
            )
        }
    }

    return modules
        .filter { it.lessons.isNotEmpty() }
        .map { module ->
            CourseModule(
                id = module.id,
                title = module.title,
                lessons = module.lessons.map { lesson ->
                    CourseLesson(
                        id = lesson.id,
                        title = lesson.title,
                        description = lesson.description,
                        blocks = lesson.blocks.toList()
                    )
                }
            )
        }
}

private fun moduleMarkerTitle(firstLine: String): String? {
    val normalized = firstLine.trim()
    val prefixes = listOf("módulo:", "modulo:", "módulo ", "modulo ")
    val prefix = prefixes.firstOrNull { normalized.startsWith(it, ignoreCase = true) } ?: return null
    return when {
        prefix.endsWith(":") -> normalized.substringAfter(':').trim()
        normalized.length > prefix.length -> normalized.trim()
        else -> ""
    }
}

private fun labelForCourseBlock(type: String): String = when (type) {
    DynamicPageBlock.TYPE_TEXT -> "Leitura"
    DynamicPageBlock.TYPE_IMAGE -> "Material visual"
    DynamicPageBlock.TYPE_VIDEO -> "Vídeo"
    DynamicPageBlock.TYPE_AUDIO -> "Áudio"
    DynamicPageBlock.TYPE_PDF -> "Documento PDF"
    else -> "Conteúdo"
}

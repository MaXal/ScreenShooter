package shooter.service

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.PathUtil
import com.intellij.util.containers.JBIterable
import recognition.Integration
import java.awt.Image


class ImageParsingService(val project: Project) {

    data class ImageInfo(val text: String, val fileType: LanguageFileType, val canFormat: Boolean = false)


    companion object ServiceHolder {
        fun getService(project: Project): ImageParsingService {
            return ServiceManager.getService(project, ImageParsingService::class.java)
        }
    }


    fun processImage(image: Image, fileType: FileType?) {
        ApplicationManager.getApplication().executeOnPooledThread({
            processImageImpl(image, fileType)
        })
    }


    private fun processImageImpl(image: Image, fileType: FileType?) {


        var info: ImageInfo = getParsedImageInfo(image, fileType) ?: return

        info = getFormattedInfo(info)

        val language = info.fileType.language

        val option = ScratchFileService.Option.create_new_always
        val fileName = "image"
        val text = info.text
        val ext = fileType?.defaultExtension

        ApplicationManager.getApplication().invokeLater({
            val scratchFile = createScratchFile(fileName, ext, language, text, option)

            if (scratchFile != null) {
                FileEditorManager.getInstance(project).openFile(scratchFile, true)
            }
        })
    }

    fun getFormattedInfo(info: ImageInfo): ImageInfo {
        if (!info.canFormat) return info

        return ReadAction.compute<ImageInfo, RuntimeException> {
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText("name" + info.fileType.defaultExtension,
                    info.fileType, info.text)
            val result = CodeStyleManager.getInstance(project).reformat(psiFile)

            ImageInfo(result.text, info.fileType, true)
        }
    }

    private fun createScratchFile(fileName: String, ext: String?, language: Language, text: String, option: ScratchFileService.Option): VirtualFile? {
        val scratchFile = ScratchRootType.getInstance().createScratchFile(project,
                PathUtil.makeFileName(fileName, ext), language, text, option)
        return scratchFile
    }


    private fun getIndentedTextFragments(image: Image): Array<String>? {
        val resultText = Integration.instance.runForImage(image)
        return arrayOf(resultText)
    }


    private fun getParsedImageInfo(image: Image, fileType: FileType?): ImageInfo? {
        val indentedTextFragments = getIndentedTextFragments(image) ?: return null

        return ReadAction.compute<ImageInfo?, RuntimeException> { detectTypeAndProcessText(indentedTextFragments, fileType) }
    }


    fun detectTypeAndProcessText(indentedTextFragments: Array<String>, fileType: FileType?): ImageInfo? {
        val languageFileType: LanguageFileType = fileType as? LanguageFileType ?: PlainTextFileType.INSTANCE
        val textsWithErrors = filterFragmentsByFileType(indentedTextFragments, languageFileType)

        var filteredFragments = textsWithErrors.texts

        if (filteredFragments.isEmpty()) {
            //nothing to do
            filteredFragments = indentedTextFragments.toList()
        }


        val text = filteredFragments.joinToString("\n")
        return ImageInfo(text, languageFileType, !textsWithErrors.hasErrors)
    }


    data class TextsWithErrorFlag(val texts: List<String>, val hasErrors: Boolean)

    fun filterFragmentsByFileType(indentedTextFragments: Array<String>, fileType: LanguageFileType): TextsWithErrorFlag {
        var hasErrors = false


        val result = indentedTextFragments.map {
            var errors = getErrors(fileType, it)

            if (errors.size() <= 0) {
                return@map it
            }

            val lines = it.split("\n")

            val newLines = removeLineNumbers(removePrefixNumbers(lines))

            val joinedText = newLines.joinToString("\n")

            errors = getErrors(fileType, joinedText)
            if (errors.size() <= 0) {
                return@map joinedText
            }

            hasErrors = true

            return@map joinedText

        }.filterNotNull()

        return TextsWithErrorFlag(result, hasErrors)
    }

    private fun getErrors(fileType: LanguageFileType, it: String): JBIterable<PsiErrorElement> {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText("foo", fileType, it)
        val errors = SyntaxTraverser.psiTraverser(psiFile).traverse().filter(PsiErrorElement::class.java)
        return errors
    }

    private fun removePrefixNumbers(lines: List<String>): List<String> {
        val mutableList = lines.toMutableList()

        @Suppress("LoopToCallChain")
        for (line in lines) {
            if (line.isEmpty()) continue

            try {
                Integer.parseInt(line)
                mutableList.remove(line)
            } catch (e: NumberFormatException) {
                break
            }
        }

        return mutableList
    }

    private fun removeLineNumbers(lines: List<String>): List<String> {
        return lines.map {
            if (it.isEmpty()) return@map it

            if (it.startsWith(":") && it.length > 1 && it[1].isDigit() ||
                    it[0].isDigit()) {
                var i = if (it.startsWith(":")) 1 else 0

                while (it.length > i && it[i].isDigit()) {
                    i++
                }

                if (i == it.length) return@map ""

                if (i < it.length && it[i].isWhitespace()) {
                    return@map it.substring(i)
                }

                return@map it
            }

            return@map it
        }
    }
}
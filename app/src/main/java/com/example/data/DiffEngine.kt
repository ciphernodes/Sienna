package com.example.data

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String
)

data class FileDiffProposal(
    val relativePath: String,
    val originalContent: String,
    val proposedContent: String,
    val diffLines: List<DiffLine>
)

object DiffEngine {

    fun generateDiff(relativePath: String, originalContent: String, proposedContent: String): FileDiffProposal {
        val oldLines = originalContent.split("\n")
        val newLines = proposedContent.split("\n")

        val resultLines = mutableListOf<DiffLine>()

        val lcs = computeLcs(oldLines, newLines)
        var i = 0
        var j = 0
        var k = 0
        var oldLineNo = 1
        var newLineNo = 1

        while (i < oldLines.size || j < newLines.size) {
            if (i < oldLines.size && j < newLines.size && k < lcs.size && oldLines[i] == lcs[k] && newLines[j] == lcs[k]) {
                resultLines.add(DiffLine(DiffLineType.UNCHANGED, oldLineNo, newLineNo, oldLines[i]))
                i++
                j++
                k++
                oldLineNo++
                newLineNo++
            } else if (i < oldLines.size && (k >= lcs.size || oldLines[i] != lcs[k])) {
                resultLines.add(DiffLine(DiffLineType.REMOVED, oldLineNo, null, oldLines[i]))
                i++
                oldLineNo++
            } else if (j < newLines.size && (k >= lcs.size || newLines[j] != lcs[k])) {
                resultLines.add(DiffLine(DiffLineType.ADDED, null, newLineNo, newLines[j]))
                j++
                newLineNo++
            }
        }

        return FileDiffProposal(
            relativePath = relativePath,
            originalContent = originalContent,
            proposedContent = proposedContent,
            diffLines = resultLines
        )
    }

    private fun computeLcs(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                lcs.add(0, a[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        return lcs
    }
}

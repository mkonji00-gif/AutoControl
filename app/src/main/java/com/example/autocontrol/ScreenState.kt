package com.example.autocontrol

import java.util.Locale

data class ScreenState(
    val kind: Kind,
    val headline: String,
    val detail: String,
    val stage: Int? = null,
    val total: Int? = null
) {
    enum class Kind { GAME, STAGE, REWARD, END_CARD, UNKNOWN }

    val id: String get() = "${kind.name}:${stage ?: 0}:${total ?: 0}"
}

/**
 * Text-only heuristics: recognizes ad sequence numbers and a few visible labels.
 * Does not infer that an icon is safe to press, does not automate input.
 */
object ScreenStateClassifier {
    private val stageRegex = Regex("(?:AD|광고)\\s*([0-9]{1,2})\\s*(?:OF|/|중)\\s*([0-9]{1,2})", RegexOption.IGNORE_CASE)
    private val rewardRegex = Regex("REWARD\\s*GRANTED|보상\\s*(?:지급|획득)", RegexOption.IGNORE_CASE)

    fun detect(raw: String): ScreenState {
        val normalized = raw.uppercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val compact = normalized.replace(" ", "")

        // Screen-specific markers chosen from the user-supplied Smash Party screenshot.
        val seemsGame = compact.contains("보물상자열기") ||
            (compact.contains("성장한계") && compact.contains("보물상자")) ||
            (compact.contains("+1200") && compact.contains("보물상자"))
        if (seemsGame) return ScreenState(ScreenState.Kind.GAME, "게임 화면 후보", "스매쉬파티 장비 화면 관련 문구를 감지했습니다. 재화 지급 여부는 별도로 확인하세요.")

        val stage = stageRegex.find(normalized)
        if (stage != null) {
            val n = stage.groupValues[1].toIntOrNull()
            val m = stage.groupValues[2].toIntOrNull()
            if (n != null && m != null && n in 1..m && m <= 20) {
                return ScreenState(ScreenState.Kind.STAGE, "광고 단계: $n / $m", "현재 영상 단계 표시를 인식했습니다. 종료 가능 여부는 이 정보만으로 판단할 수 없습니다.", n, m)
            }
        }
        if (rewardRegex.containsMatchIn(normalized)) {
            return ScreenState(ScreenState.Kind.REWARD, "보상 문구 감지", "Reward granted 표시가 있습니다. 게임 복귀·보상 반영 여부는 별도 확인이 필요합니다.")
        }
        if (normalized.contains("GOOGLE PLAY") || compact.contains("설치하기") || normalized.contains("DOWNLOAD") || compact.contains("다운로드해")) {
            return ScreenState(ScreenState.Kind.END_CARD, "설치 안내 화면 후보", "광고 설치 안내 문구가 감지되었습니다. 화면에 X가 여러 개 있을 수 있습니다.")
        }
        return ScreenState(ScreenState.Kind.UNKNOWN, "화면 분석 중", "읽을 수 있는 단계/보상 문구가 없습니다. X·≫·▶| 아이콘은 OCR만으로 확정할 수 없습니다.")
    }
}

package com.babybloom.data.local.seeder

import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.LearningContentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningContentSeeder @Inject constructor(
    private val learningContentDao: LearningContentDao
) {

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (learningContentDao.count() > 0) return@withContext  // already seeded

        learningContentDao.insertAll(getData())
    }

    private fun getData(): List<LearningContentEntity> = buildList {
        addAll(nameOfLetters())
        addAll(soundOfLetters())
        addAll(numbers())
        addAll(animals())
        addAll(shapes())
        addAll(colors())
    }

    // ─────────────────────────────────────────
    // LETTERS
    // ─────────────────────────────────────────
    private fun nameOfLetters() = listOf(
        LearningContentEntity(
            id             = "letter_alef",
            labelAr        = "أَلِف",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "letter_ba",
            labelAr        = "بَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 2
        ),
        LearningContentEntity(
            id             = "letter_ta",
            labelAr        = "تَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 2,
            learningOrder = 8
        ),
        LearningContentEntity(
            id             = "letter_tha",
            labelAr        = "ثَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 5,
            learningOrder = 27
        ),
        LearningContentEntity(
            id             = "letter_jeem",
            labelAr        = "جِيم",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 5
        ),
        LearningContentEntity(
            id             = "letter_ha",
            labelAr        = "حَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 4
        ),
        LearningContentEntity(
            id             = "letter_kha",
            labelAr        = "خَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 2,
            learningOrder = 7
        ),
        LearningContentEntity(
            id             = "letter_dal",
            labelAr        = "دَال",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 6
        ),
        LearningContentEntity(
            id             = "letter_thal",
            labelAr        = "ذَال",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 21
        ),
        LearningContentEntity(
            id             = "letter_ra",
            labelAr        = "رَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 12
        ),
        LearningContentEntity(
            id             = "letter_zay",
            labelAr        = "زَاي",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 23
        ),
        LearningContentEntity(
            id             = "letter_seen",
            labelAr        = "سِين",
            category       = Category.LETTER_NAME,
            difficultyLevel = 2,
            learningOrder = 10
        ),
        LearningContentEntity(
            id             = "letter_sheen",
            labelAr        = "شِين",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 18
        ),
        LearningContentEntity(
            id             = "letter_sad",
            labelAr        = "صَاد",
            category       = Category.LETTER_NAME,
            difficultyLevel = 5,
            learningOrder = 25
        ),
        LearningContentEntity(
            id             = "letter_dad",
            labelAr        = "ضَاد",
            category       = Category.LETTER_NAME,
            difficultyLevel = 5,
            learningOrder = 26
        ),
        LearningContentEntity(
            id             = "letter_tah",
            labelAr        = "طَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 5,
            learningOrder = 24
        ),
        LearningContentEntity(
            id             = "letter_zah",
            labelAr        = "ظَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 22
        ),
        LearningContentEntity(
            id             = "letter_ain",
            labelAr        = "عَيْن",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 17
        ),
        LearningContentEntity(
            id             = "letter_ghain",
            labelAr        = "غَيْن",
            category       = Category.LETTER_NAME,
            difficultyLevel = 5,
            learningOrder = 28
        ),
        LearningContentEntity(
            id             = "letter_fa",
            labelAr        = "فَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 13
        ),
        LearningContentEntity(
            id             = "letter_qaf",
            labelAr        = "قَاف",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 15
        ),
        LearningContentEntity(
            id             = "letter_kaf",
            labelAr        = "كَاف",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 14
        ),
        LearningContentEntity(
            id             = "letter_lam",
            labelAr        = "لَام",
            category       = Category.LETTER_NAME,
            difficultyLevel = 2,
            learningOrder = 9
        ),
        LearningContentEntity(
            id             = "letter_meem",
            labelAr        = "مِيم",
            category       = Category.LETTER_NAME,
            difficultyLevel = 1,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "letter_noon",
            labelAr        = "نُون",
            category       = Category.LETTER_NAME,
            difficultyLevel = 2,
            learningOrder = 11
        ),
        LearningContentEntity(
            id             = "letter_ha2",
            labelAr        = "هَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 20
        ),
        LearningContentEntity(
            id             = "letter_waw",
            labelAr        = "وَاو",
            category       = Category.LETTER_NAME,
            difficultyLevel = 4,
            learningOrder = 19
        ),
        LearningContentEntity(
            id             = "letter_ya",
            labelAr        = "يَاء",
            category       = Category.LETTER_NAME,
            difficultyLevel = 3,
            learningOrder = 16
        )

    )
    private fun soundOfLetters() = listOf(
        LearningContentEntity(
            id             = "letter_alef_s",
            labelAr        = "أَلِف",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "letter_ba_s",
            labelAr        = "بَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 2
        ),
        LearningContentEntity(
            id             = "letter_ta_s",
            labelAr        = "تَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 2,
            learningOrder = 8
        ),
        LearningContentEntity(
            id             = "letter_tha_s",
            labelAr        = "ثَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 5,
            learningOrder = 27
        ),
        LearningContentEntity(
            id             = "letter_jeem_s",
            labelAr        = "جِيم",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 5
        ),
        LearningContentEntity(
            id             = "letter_ha_s",
            labelAr        = "حَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 4
        ),
        LearningContentEntity(
            id             = "letter_kha_s",
            labelAr        = "خَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 2,
            learningOrder = 7
        ),
        LearningContentEntity(
            id             = "letter_dal_s",
            labelAr        = "دَال",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 6
        ),
        LearningContentEntity(
            id             = "letter_thal_s",
            labelAr        = "ذَال",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 21
        ),
        LearningContentEntity(
            id             = "letter_ra_s",
            labelAr        = "رَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 12
        ),
        LearningContentEntity(
            id             = "letter_zay_s",
            labelAr        = "زَاي",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 23
        ),
        LearningContentEntity(
            id             = "letter_seen_s",
            labelAr        = "سِين",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 2,
            learningOrder = 10
        ),
        LearningContentEntity(
            id             = "letter_sheen_s",
            labelAr        = "شِين",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 18
        ),
        LearningContentEntity(
            id             = "letter_sad_s",
            labelAr        = "صَاد",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 5,
            learningOrder = 25
        ),
        LearningContentEntity(
            id             = "letter_dad_s",
            labelAr        = "ضَاد",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 5,
            learningOrder = 26
        ),
        LearningContentEntity(
            id             = "letter_tah_s",
            labelAr        = "طَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 5,
            learningOrder = 24
        ),
        LearningContentEntity(
            id             = "letter_zah_s",
            labelAr        = "ظَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 22
        ),
        LearningContentEntity(
            id             = "letter_ain_s",
            labelAr        = "عَيْن",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 17
        ),
        LearningContentEntity(
            id             = "letter_ghain_s",
            labelAr        = "غَيْن",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 5,
            learningOrder = 28
        ),
        LearningContentEntity(
            id             = "letter_fa_s",
            labelAr        = "فَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 13
        ),
        LearningContentEntity(
            id             = "letter_qaf_s",
            labelAr        = "قَاف",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 15
        ),
        LearningContentEntity(
            id             = "letter_kaf_s",
            labelAr        = "كَاف",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 14
        ),
        LearningContentEntity(
            id             = "letter_lam_s",
            labelAr        = "لَام",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 2,
            learningOrder = 9
        ),
        LearningContentEntity(
            id             = "letter_meem_s",
            labelAr        = "مِيم",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 1,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "letter_noon_s",
            labelAr        = "نُون",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 2,
            learningOrder = 11
        ),
        LearningContentEntity(
            id             = "letter_ha2_s",
            labelAr        = "هَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 20
        ),
        LearningContentEntity(
            id             = "letter_waw_s",
            labelAr        = "وَاو",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 4,
            learningOrder = 19
        ),
        LearningContentEntity(
            id             = "letter_ya_s",
            labelAr        = "يَاء",
            category       = Category.LETTER_SOUND,
            difficultyLevel = 3,
            learningOrder = 16
        )

    )
    // ─────────────────────────────────────────
    // NUMBERS
    // ─────────────────────────────────────────
    private fun numbers() = listOf(
        LearningContentEntity(
            id             = "number_1",
            labelAr        = "وَاحِد",
            category       = Category.NUMBER,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "number_2",
            labelAr        = "اِثْنَان",
            category       = Category.NUMBER,
            difficultyLevel = 1,
            learningOrder = 2
        ),
        LearningContentEntity(
            id             = "number_3",
            labelAr        = "ثَلَاثَة",
            category       = Category.NUMBER,
            difficultyLevel = 1,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "number_4",
            labelAr        = "أَرْبَعَة",
            category       = Category.NUMBER,
            difficultyLevel = 2,
            learningOrder = 4
        ),
        LearningContentEntity(
            id             = "number_5",
            labelAr        = "خَمْسَة",
            category       = Category.NUMBER,
            difficultyLevel = 2,
            learningOrder = 5
        ),
        LearningContentEntity(
            id             = "number_6",
            labelAr        = "سِتَّة",
            category       = Category.NUMBER,
            difficultyLevel = 2,
            learningOrder = 6
        ),
        LearningContentEntity(
            id             = "number_7",
            labelAr        = "سَبْعَة",
            category       = Category.NUMBER,
            difficultyLevel = 3,
            learningOrder = 7
        ),
        LearningContentEntity(
            id             = "number_8",
            labelAr        = "ثَمَانِيَة",
            category       = Category.NUMBER,
            difficultyLevel = 4,
            learningOrder = 8
        ),
        LearningContentEntity(
            id             = "number_9",
            labelAr        = "تِسْعَة",
            category       = Category.NUMBER,
            difficultyLevel = 3,
            learningOrder = 9
        ),
        LearningContentEntity(
            id             = "number_10",
            labelAr        = "عَشَرَة",
            category       = Category.NUMBER,
            difficultyLevel = 4,
            learningOrder = 10
        )

    )

    // ─────────────────────────────────────────
    // ANIMALS
    // ─────────────────────────────────────────
    private fun animals() = listOf(
        LearningContentEntity(
            id             = "animal_crocodile",
            labelAr        = "تِمْسَاح",
            category       = Category.ANIMAL,
            difficultyLevel = 2,
            learningOrder = 8
        ),
        LearningContentEntity(
            id             = "animal_snake",
            labelAr        = "ثُعْبَان",
            category       = Category.ANIMAL,
            difficultyLevel = 5,
            learningOrder = 27
        ),
        LearningContentEntity(
            id             = "animal_camel",
            labelAr        = "جَمَل",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 5
        ),
        LearningContentEntity(
            id             = "animal_horse",
            labelAr        = "حِصَان",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 4
        ),
        LearningContentEntity(
            id             = "animal_sheep",
            labelAr        = "خَرُوف",
            category       = Category.ANIMAL,
            difficultyLevel = 2,
            learningOrder = 7
        ),
        LearningContentEntity(
            id             = "animal_bear",
            labelAr        = "دُب",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 6
        ),
        LearningContentEntity(
            id             = "animal_wolf",
            labelAr        = "ذِئْب",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 21
        ),
        LearningContentEntity(
            id             = "animal_shrimp",
            labelAr        = "رُوبِيَان",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 12
        ),
        LearningContentEntity(
            id             = "animal_giraffe",
            labelAr        = "زَرَافَة",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 23
        ),
        LearningContentEntity(
            id             = "animal_fish",
            labelAr        = "سَمَكَة",
            category       = Category.ANIMAL,
            difficultyLevel = 2,
            learningOrder = 10
        ),
        LearningContentEntity(
            id             = "animal_chimpanzee",
            labelAr        = "شِمْبَانْزِي",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 18
        ),
        LearningContentEntity(
            id             = "animal_falcon",
            labelAr        = "صَقْر",
            category       = Category.ANIMAL,
            difficultyLevel = 5,
            learningOrder = 25
        ),
        LearningContentEntity(
            id             = "animal_frog",
            labelAr        = "ضِفْدَع",
            category       = Category.ANIMAL,
            difficultyLevel = 5,
            learningOrder = 26
        ),
        LearningContentEntity(
            id             = "animal_peacock",
            labelAr        = "طَاوُوس",
            category       = Category.ANIMAL,
            difficultyLevel = 5,
            learningOrder = 24
        ),
        LearningContentEntity(
            id             = "animal_deer",
            labelAr        = "ظَبْي",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 22
        ),
        LearningContentEntity(
            id             = "animal_spider",
            labelAr        = "عَنْكَبُوت",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 17
        ),
        LearningContentEntity(
            id             = "animal_gazelle",
            labelAr        = "غَزَالَة",
            category       = Category.ANIMAL,
            difficultyLevel = 5,
            learningOrder = 28
        ),
        LearningContentEntity(
            id             = "animal_elephant",
            labelAr        = "فِيل",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 13
        ),
        LearningContentEntity(
            id             = "animal_monkey",
            labelAr        = "قِرْد",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 15
        ),
        LearningContentEntity(
            id             = "animal_dog",
            labelAr        = "كَلْب",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 14
        ),
        LearningContentEntity(
            id             = "animal_llama",
            labelAr        = "لَامَا",
            category       = Category.ANIMAL,
            difficultyLevel = 2,
            learningOrder = 9
        ),
        LearningContentEntity(
            id             = "animal_goat",
            labelAr        = "مَعْزَة",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "animal_tiger",
            labelAr        = "نَمِر",
            category       = Category.ANIMAL,
            difficultyLevel = 2,
            learningOrder = 11
        ),
        LearningContentEntity(
            id             = "animal_hoopoe",
            labelAr        = "هُدْهُد",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 20
        ),
        LearningContentEntity(
            id             = "animal_rhinoceros",
            labelAr        = "وَحِيدُ القَرْن",
            category       = Category.ANIMAL,
            difficultyLevel = 4,
            learningOrder = 19
        ),
        LearningContentEntity(
            id             = "animal_dove",
            labelAr        = "يَمَامَة",
            category       = Category.ANIMAL,
            difficultyLevel = 3,
            learningOrder = 16
        ),
        LearningContentEntity(
            id             = "animal_lion",
            labelAr        = "أَسَد",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "animal_duck",
            labelAr        = "بَطَّة",
            category       = Category.ANIMAL,
            difficultyLevel = 1,
            learningOrder = 2
        )
    )

    // ─────────────────────────────────────────
    // SHAPES
    // ─────────────────────────────────────────
    private fun shapes() = listOf(
        LearningContentEntity(
            id             = "shape_circle",
            labelAr        = "دَائِرَة",
            category       = Category.SHAPE,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "shape_square",
            labelAr        = "مُرَبَّع",
            category       = Category.SHAPE,
            difficultyLevel = 2,
            learningOrder = 2
        ),
        LearningContentEntity(
            id             = "shape_triangle",
            labelAr        = "مُثَلَّث",
            category       = Category.SHAPE,
            difficultyLevel = 3,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "shape_rectangle",
            labelAr        = "مُسْتَطِيل",
            category       = Category.SHAPE,
            difficultyLevel = 4,
            learningOrder = 4
        )
    )

    // ─────────────────────────────────────────
    // COLORS
    // ─────────────────────────────────────────
    private fun colors() = listOf(
        LearningContentEntity(
            id             = "color_red",
            labelAr        = "أَحْمَر",
            category       = Category.COLOR,
            difficultyLevel = 1,
            learningOrder = 1
        ),
        LearningContentEntity(
            id             = "color_blue",
            labelAr        = "أَزْرَق",
            category       = Category.COLOR,
            difficultyLevel = 1,
            learningOrder = 2
        ),
        LearningContentEntity(
            id             = "color_yellow",
            labelAr        = "أَصْفَر",
            category       = Category.COLOR,
            difficultyLevel = 2,
            learningOrder = 3
        ),
        LearningContentEntity(
            id             = "color_green",
            labelAr        = "أَخْضَر",
            category       = Category.COLOR,
            difficultyLevel = 2,
            learningOrder = 4
        ),
        LearningContentEntity(
            id             = "color_black",
            labelAr        = "أَسْوَد",
            category       = Category.COLOR,
            difficultyLevel = 2,
            learningOrder = 5
        ),
        LearningContentEntity(
            id             = "color_white",
            labelAr        = "أَبْيَض",
            category       = Category.COLOR,
            difficultyLevel = 2,
            learningOrder = 6
        ),
        LearningContentEntity(
            id             = "color_orange",
            labelAr        = "بُرْتُقَالِي",
            category       = Category.COLOR,
            difficultyLevel = 3,
            learningOrder = 7
        ),
        LearningContentEntity(
            id             = "color_purple",
            labelAr        = "بَنَفْسَجِي",
            category       = Category.COLOR,
            difficultyLevel = 4,
            learningOrder = 8
        ),
        LearningContentEntity(
            id             = "color_pink",
            labelAr        = "وَرْدِي",
            category       = Category.COLOR,
            difficultyLevel = 3,
            learningOrder = 9
        ),
        LearningContentEntity(
            id             = "color_brown",
            labelAr        = "بُنِّي",
            category       = Category.COLOR,
            difficultyLevel = 3,
            learningOrder = 10
        )
    )
}
package com.example.familysafetube

/** Built-in knowledge about a picture label: its Arabic name and (optionally) a bundled animal sound. */
data class CatalogEntry(val ar: String, val soundKey: String? = null)

/**
 * Maps the English label taken from a picture's file name ("animals_lion.png" -> "Lion") to the Arabic
 * name the quiz speaks, so the quiz works out of the box without any editing. The parent can override
 * any name in the editor. Arabic names are a first pass and worth a native-speaker review.
 */
object QuizCatalog {
    /** Keys of the animal sounds shipped in res/raw (see BuiltInSounds). */
    val SOUND_KEYS = setOf("dog", "cat", "cow", "sheep", "pig", "rooster", "hen", "frog", "crow", "bird")

    private val ENTRIES: Map<String, CatalogEntry> = buildMap {
        fun add(en: String, ar: String, sound: String? = null) { put(en, CatalogEntry(ar, sound)) }
        // animals with a bundled sound
        add("dog", "كلب", "dog"); add("puppy", "جرو", "dog")
        add("cat", "قطة", "cat"); add("kitten", "قطة صغيرة", "cat")
        add("cow", "بقرة", "cow"); add("sheep", "خروف", "sheep"); add("lamb", "خروف صغير", "sheep")
        add("pig", "خنزير", "pig"); add("rooster", "ديك", "rooster")
        add("hen", "دجاجة", "hen"); add("chicken", "دجاجة", "hen")
        add("frog", "ضفدع", "frog"); add("crow", "غراب", "crow"); add("bird", "عصفور", "bird")
        // other animals (add your own sound in the editor)
        add("lion", "أسد"); add("tiger", "نمر"); add("elephant", "فيل"); add("giraffe", "زرافة")
        add("zebra", "حمار وحشي"); add("monkey", "قرد"); add("bear", "دب"); add("polar bear", "دب قطبي")
        add("horse", "حصان"); add("goat", "ماعز"); add("duck", "بطة"); add("goose", "إوزة")
        add("turkey", "ديك رومي"); add("fish", "سمكة"); add("camel", "جمل"); add("donkey", "حمار")
        add("rabbit", "أرنب"); add("fox", "ثعلب"); add("wolf", "ذئب"); add("deer", "غزال")
        add("turtle", "سلحفاة"); add("snake", "ثعبان"); add("crocodile", "تمساح"); add("owl", "بومة")
        add("eagle", "نسر"); add("falcon", "صقر"); add("parrot", "ببغاء"); add("penguin", "بطريق")
        add("dolphin", "دلفين"); add("whale", "حوت"); add("shark", "قرش"); add("butterfly", "فراشة")
        add("bee", "نحلة"); add("ant", "نملة"); add("squirrel", "سنجاب"); add("hippo", "فرس النهر")
        add("rhino", "وحيد القرن"); add("kangaroo", "كنغر"); add("panda", "باندا"); add("mouse", "فأر")
        add("hedgehog", "قنفذ"); add("octopus", "أخطبوط"); add("crab", "سلطعون"); add("spider", "عنكبوت")
        add("ladybug", "دعسوقة")
        // fruits and vegetables
        add("apple", "تفاحة"); add("banana", "موزة"); add("orange", "برتقالة"); add("grapes", "عنب")
        add("strawberry", "فراولة"); add("watermelon", "بطيخ"); add("melon", "شمام"); add("mango", "مانجو")
        add("lemon", "ليمونة"); add("pineapple", "أناناس"); add("pear", "كمثرى"); add("peach", "خوخ")
        add("cherry", "كرز"); add("dates", "تمر"); add("date", "تمر"); add("pomegranate", "رمان")
        add("fig", "تين"); add("tomato", "طماطم"); add("carrot", "جزرة"); add("potato", "بطاطس")
        add("cucumber", "خيار"); add("onion", "بصل"); add("corn", "ذرة"); add("broccoli", "بروكلي")
        add("pepper", "فلفل"); add("eggplant", "باذنجان"); add("lettuce", "خس"); add("pumpkin", "يقطين")
        add("garlic", "ثوم"); add("mushroom", "فطر")
    }

    val size: Int get() = ENTRIES.size

    /** Looks a label up case-insensitively, also trying a simple singular form ("lions" -> "lion"). */
    fun lookup(label: String): CatalogEntry? {
        val key = label.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")
        ENTRIES[key]?.let { return it }
        if (key.endsWith("es")) ENTRIES[key.dropLast(2)]?.let { return it }
        if (key.endsWith("s")) ENTRIES[key.dropLast(1)]?.let { return it }
        return null
    }
}

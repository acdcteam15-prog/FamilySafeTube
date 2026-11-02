package com.example.familysafetube

/**
 * The animal sounds shipped inside the app (res/raw). Referenced statically (not by name lookup) so
 * resource shrinking can never strip them. Keys must match [QuizCatalog.SOUND_KEYS].
 * All clips are CC0 (public domain); see docs/SOUND-CREDITS.md.
 */
object BuiltInSounds {
    fun resFor(key: String): Int? = when (key) {
        "dog" -> R.raw.animal_dog
        "cat" -> R.raw.animal_cat
        "cow" -> R.raw.animal_cow
        "sheep" -> R.raw.animal_sheep
        "pig" -> R.raw.animal_pig
        "rooster" -> R.raw.animal_rooster
        "hen" -> R.raw.animal_hen
        "frog" -> R.raw.animal_frog
        "crow" -> R.raw.animal_crow
        "bird" -> R.raw.animal_bird
        else -> null
    }
}

# Sound credits

The animal sounds in `app/src/main/res/raw/` are short (2.2 s) excerpts from clips in the
**ESC-50** dataset (https://github.com/karolpiczak/ESC-50, Karol J. Piczak). Each clip was originally
published on Freesound. ESC-50's own `LICENSE` file records the license of every individual clip; **only clips
marked CC0 (public domain dedication) were used**, so no attribution is legally required. They are credited here anyway.

The excerpts were trimmed to the loudest 2.2 s window, faded in/out, loudness-normalised to about -18 LUFS,
down-mixed to mono 22.05 kHz and encoded as Ogg Vorbis. Nothing else was changed.

| File | Sound | ESC-50 clip | Freesound author | License | Original |
|---|---|---|---|---|---|
| `animal_dog.ogg` | Dog bark | 5-208030-A-0.wav | Mewsel | CC0 | http://www.freesound.org/people/Mewsel/sounds/208030/ |
| `animal_cat.ogg` | Cat meow | 4-133047-C-5.wav | temawas | CC0 | http://www.freesound.org/people/temawas/sounds/133047/ |
| `animal_cow.ogg` | Cow moo | 5-194899-D-3.wav | lolamadeus | CC0 | http://www.freesound.org/people/lolamadeus/sounds/194899/ |
| `animal_sheep.ogg` | Sheep bleat | 3-140199-C-8.wav | felix.blume | CC0 | http://www.freesound.org/people/felix.blume/sounds/140199/ |
| `animal_pig.ogg` | Pig | 4-132383-B-2.wav | ideiasemvideo | CC0 | http://www.freesound.org/people/ideiasemvideo/sounds/132383/ |
| `animal_rooster.ogg` | Rooster crow | 4-164859-A-1.wav | bob58 | CC0 | http://www.freesound.org/people/bob58/sounds/164859/ |
| `animal_hen.ogg` | Hen | 4-200330-A-6.wav | jsbarrett | CC0 | http://www.freesound.org/people/jsbarrett/sounds/200330/ |
| `animal_frog.ogg` | Frog | 5-187201-B-4.wav | maphill | CC0 | http://www.freesound.org/people/maphill/sounds/187201/ |
| `animal_crow.ogg` | Crow caw | 1-75162-A-9.wav | nigelcoop | CC0 | http://www.freesound.org/people/nigelcoop/sounds/75162/ |
| `animal_bird.ogg` | Chirping bird | 2-122616-A-14.wav | urupin | CC0 | http://www.freesound.org/people/urupin/sounds/122616/ |

## What is not included
There is no lion, horse, elephant, duck, donkey, camel, goat or monkey sound. I could not find freely
licensed recordings of these that I could verify. Add your own in the app: Settings -> Edit quiz cards ->
choose the card -> record from the phone microphone or import an audio file.

## Not verified by listening
The clips were chosen automatically (signal level, how much of the window contains sound, noise floor,
clipping) and could not be listened to when the app was built. Please play each one in the editor once; if any
sounds poor, replace it there (record or import) and untick "Use the built-in sound".

The cheer and "oops" feedback sounds are generated in code (`ToneSynth.kt`) and need no credit.

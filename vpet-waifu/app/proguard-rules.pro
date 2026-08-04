# Room generates implementations reflectively referenced by the runtime.
-keep class com.vpet.waifu.data.db.** { *; }

# WorkManager instantiates workers by class name.
-keep class com.vpet.waifu.work.** { *; }

# A sprite pack names the states it draws as strings in its manifest, and
# SpritePet looks them up with PetState.valueOf. Minification is free to rename
# enum constants, and the failure would be silent and release-only: every clip
# would fall back to the idle frame and the character would stop animating in
# the shipped build while working perfectly in debug.
-keepclassmembers enum com.vpet.waifu.domain.PetState {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

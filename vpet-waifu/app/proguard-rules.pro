# Room generates implementations reflectively referenced by the runtime.
-keep class com.vpet.waifu.data.db.** { *; }

# WorkManager instantiates workers by class name.
-keep class com.vpet.waifu.work.** { *; }

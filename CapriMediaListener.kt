package app.capri.mobile

import android.service.notification.NotificationListenerService

// Hanya dibutuhkan supaya Android mengizinkan Capri membaca sesi media (judul lagu dan tombol putar).
class CapriMediaListener : NotificationListenerService()

package com.jahid.wallpaperextract

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var btnSave: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestAllFilesAccess()

        btnSave = findViewById(R.id.saveButton)
        btnSave.setOnClickListener {
            if (hasStoragePermission()) {
                vibrate()
                saveCurrentWallpaper()
            } else {
                Toast.makeText(this, "Permission required!", Toast.LENGTH_SHORT).show()
            }
        }

        // Check permission immediately on app start
        checkPermissionsOnStartup()
    }

    private fun checkAndRequestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Launch settings page for "All files access"
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun checkPermissionsOnStartup() {
        if (hasStoragePermission()) {
            showWallpaperPreview()
            btnSave.isEnabled = true
        } else {
            requestStoragePermission()
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            showWallpaperPreview()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
            Toast.makeText(this, "Permission needed to access wallpapers", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(requiredPermission), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showWallpaperPreview()
                btnSave.isEnabled = true
            } else {
                btnSave.isEnabled = false
                Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showWallpaperPreview() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable
            val wallpaperPreview = findViewById<ImageView>(R.id.wallpaperPreview)

            if (wallpaperDrawable is BitmapDrawable) {
                wallpaperPreview.setImageDrawable(wallpaperDrawable)
            } else {
                wallpaperPreview.setImageResource(R.drawable.placeholder_live_wallpaper)
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "SecurityException: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCurrentWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable

            val bitmap = (wallpaperDrawable as? BitmapDrawable)?.bitmap ?: run {
                Toast.makeText(this, "Not a static wallpaper", Toast.LENGTH_SHORT).show()
                return
            }

            val filename = "wallpaper_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WallpaperExtractor")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        if (bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(uri, contentValues, null, null)
                            }
                            Toast.makeText(this, "Wallpaper saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Compression failed", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(this, "Failed to open stream", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "IO Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(this, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Toast.makeText(this, "SecurityException: Check permissions", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

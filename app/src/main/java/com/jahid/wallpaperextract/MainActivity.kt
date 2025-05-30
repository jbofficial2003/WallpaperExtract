package com.jahid.wallpaperextract

import android.widget.ImageView
import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wallpaperManager = WallpaperManager.getInstance(this)
        val wallpaperDrawable = wallpaperManager.drawable
        val wallpaperPreview = findViewById<ImageView>(R.id.wallpaperPreview)
        wallpaperPreview.setImageDrawable(wallpaperDrawable)

        val btnSave = findViewById<Button>(R.id.saveButton)
        btnSave.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED) {
            saveCurrentWallpaper()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(requiredPermission),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveCurrentWallpaper()
            } else {
                Toast.makeText(
                    this,
                    "Permission required to save wallpapers",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveCurrentWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable

            // Safe cast with null check
            val bitmap = (wallpaperDrawable as? BitmapDrawable)?.bitmap ?: run {
                Toast.makeText(this, "Current wallpaper is not a static image", Toast.LENGTH_SHORT).show()
                return
            }

            val filename = "wallpaper_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wallpapers")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        if (bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                            // Update IS_PENDING for Android Q+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(uri, contentValues, null, null)
                            }
                            Toast.makeText(this, "Wallpaper saved (PNG, lossless)!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Compression failed", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(this, "Failed to open output stream", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(this, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}

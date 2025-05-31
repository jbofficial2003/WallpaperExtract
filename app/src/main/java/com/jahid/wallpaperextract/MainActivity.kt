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
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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
        Toast.makeText(this, "WallpaperExtract made by Jahid", Toast.LENGTH_SHORT).show()
        btnSave = findViewById(R.id.saveButton)
        btnSave.setOnClickListener {
            if (hasStoragePermission()) {
                vibrate()
                saveCurrentWallpaper()
            } else {
                Toast.makeText(this, "Permission required!", Toast.LENGTH_SHORT).show()
            }
        }
        val wallpaperPreview = findViewById<ImageView>(R.id.wallpaperPreview)
        wallpaperPreview.setOnLongClickListener {
            shareWallpaperImage()
            true
        }
        checkPermissionsOnStartup()
    }
    private fun shareWallpaperImage() {
        val wallpaperPreview = findViewById<ImageView>(R.id.wallpaperPreview)
        val drawable = wallpaperPreview.drawable

        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            try {
                // Save to cache
                val cachePath = File(externalCacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "wallpaper_share.png")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                // Get URI using FileProvider
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Wallpaper"))

            } catch (e: IOException) {
                Toast.makeText(this, "Failed to share wallpaper", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "No wallpaper to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestAllFilesAccess() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
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
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
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
        val requiredPermission =
            Manifest.permission.READ_MEDIA_IMAGES
        return ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermission() {
        val requiredPermission =
            Manifest.permission.READ_MEDIA_IMAGES

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
            val filename = "wallpaper.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WallpaperExtractor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        if (bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(uri, contentValues, null, null)
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
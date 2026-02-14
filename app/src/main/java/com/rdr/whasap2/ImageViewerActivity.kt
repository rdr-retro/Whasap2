package com.rdr.whasap2

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.FileOutputStream

class ImageViewerActivity : AppCompatActivity() {

    private var imageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        imageUrl = intent.getStringExtra("IMAGE_URL")

        val imageView = findViewById<ImageView>(R.id.image_fullscreen)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val btnDownload = findViewById<ImageView>(R.id.btn_download)

        // Load full-size image
        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_close_clear_cancel)
                .into(imageView)
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnDownload.setOnClickListener {
            downloadImage()
        }
    }

    private fun downloadImage() {
        if (imageUrl == null) return

        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    saveImageToGallery(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Toast.makeText(this@ImageViewerActivity, "Error al descargar", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val filename = "Whasap2_${System.currentTimeMillis()}.jpg"

            // Use MediaStore for saving (works on all API levels)
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            // Try MediaStore first (API 29+), fall back to direct file write
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                    Toast.makeText(this, "Imagen guardada ✓", Toast.LENGTH_SHORT).show()
                }
            } else {
                // For older devices - save to Pictures directory
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val whasapDir = File(picturesDir, "Whasap2")
                if (!whasapDir.exists()) whasapDir.mkdirs()

                val file = File(whasapDir, filename)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }

                // Notify gallery
                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = android.net.Uri.fromFile(file)
                sendBroadcast(mediaScanIntent)

                Toast.makeText(this, "Imagen guardada en Galería ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

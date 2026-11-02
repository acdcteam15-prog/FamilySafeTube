package com.example.familysafetube

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import java.io.File

/** Size (px) the blurred backdrop is decoded at, and the blur radius (px) used on Android 12+. */
private const val BG_DECODE_PX = 128
private const val BG_BLUR_RADIUS = 48f

class ImageAdapter(
    private val imagePaths: List<String>,
    private val category: String = ""
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val dp  = parent.context.resources.displayMetrics.density
        val ctx = parent.context

        // ── Root ──────────────────────────────────────────────────────────
        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#070B14"))
        }

        // ── Blurred background ────────────────────────────────────────────
        val ivBg = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType  = ImageView.ScaleType.CENTER_CROP
            alpha      = 0.35f
            // Android 12+: real GPU blur. Older phones show the tiny picture below scaled up, which is soft anyway.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(BG_BLUR_RADIUS, BG_BLUR_RADIUS, Shader.TileMode.CLAMP))
            }
        }

        // ── Main image — centered with elegant padding ────────────────────
        val ivMain = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also {
                it.setMargins(
                    (24*dp).toInt(), (100*dp).toInt(),
                    (24*dp).toInt(), (140*dp).toInt()
                )
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // ── Bottom label card ─────────────────────────────────────────────
        val labelCard = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (120*dp).toInt(),
                Gravity.BOTTOM
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        // Image name (Arabic TTS driven)
        val tvName = TextView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            ).also {
                it.setMargins((24*dp).toInt(), 0, (24*dp).toInt(), 0)
            }
            setTextColor(Color.WHITE)
            textSize    = 28f
            gravity     = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            maxLines    = 2
        }

        // Alphabet sub-label: "A is for Apple"
        val tvSub = TextView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).also {
                it.setMargins(0, 0, 0, (12*dp).toInt())
            }
            setTextColor(Color.parseColor("#FFD700"))   // gold
            textSize = 16f
            gravity  = Gravity.CENTER
            visibility = View.GONE
        }

        labelCard.addView(tvName)
        labelCard.addView(tvSub)

        // ── Spinner ───────────────────────────────────────────────────────
        val spinner = ProgressBar(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                (56*dp).toInt(), (56*dp).toInt(), Gravity.CENTER
            )
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#4F8EF7")
            )
        }

        // ── Swipe hint ────────────────────────────────────────────────────
        val tvHint = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).also { it.topMargin = (40*dp).toInt() }
            text      = ctx.getString(R.string.img_swipe_hint)
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize  = 13f
            setPadding(
                (16*dp).toInt(), (8*dp).toInt(),
                (16*dp).toInt(), (8*dp).toInt()
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#55000000"))
                cornerRadius = 50 * dp
            }
        }

        root.addView(ivBg)
        root.addView(ivMain)
        root.addView(labelCard)
        root.addView(spinner)
        root.addView(tvHint)

        return ImageViewHolder(root, ivBg, ivMain, tvName, tvSub, spinner, tvHint)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val actual = if (imagePaths.isNotEmpty()) position % imagePaths.size else 0
        holder.bind(imagePaths[actual], category, actual, imagePaths.size)
    }

    override fun getItemCount() = if (imagePaths.isNotEmpty()) Int.MAX_VALUE else 0

    // ─────────────────────────────────────────────────────────────────────
    class ImageViewHolder(
        itemView: View,
        private val ivBg    : ImageView,
        private val ivMain  : ImageView,
        private val tvName  : TextView,
        private val tvSub   : TextView,
        private val spinner : ProgressBar,
        private val tvHint  : TextView
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(path: String, category: String, position: Int, total: Int) {
            val ctx = itemView.context
            val file = File(path)

            // Raw name — strip storage prefix e.g. "animals_cat.jpg" → "cat"
            val rawName = file.nameWithoutExtension
                .substringAfter("_")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }

            // ── Blurred background ────────────────────────────────────────
            // Loaded tiny: it is blurred anyway, so this is cheap on memory and needs no blur library.
            Glide.with(ctx)
                .load(path)
                .override(BG_DECODE_PX, BG_DECODE_PX)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(ivBg)

            // ── Main image with entrance animation ────────────────────────
            spinner.visibility = View.VISIBLE
            ivMain.alpha       = 0f
            ivMain.scaleX      = 0.88f
            ivMain.scaleY      = 0.88f

            Glide.with(ctx)
                .load(path)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerInside()
                        .override(
                            ctx.resources.displayMetrics.widthPixels,
                            ctx.resources.displayMetrics.heightPixels
                        )
                )
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, m: Any?, t: Target<Drawable>, f: Boolean): Boolean {
                        spinner.visibility = View.GONE; return false
                    }
                    override fun onResourceReady(r: Drawable, m: Any, t: Target<Drawable>?, d: DataSource, f: Boolean): Boolean {
                        spinner.visibility = View.GONE
                        // Pop-in animation
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(ivMain, "alpha", 0f, 1f),
                                ObjectAnimator.ofFloat(ivMain, "scaleX", 0.88f, 1f),
                                ObjectAnimator.ofFloat(ivMain, "scaleY", 0.88f, 1f)
                            )
                            duration    = 350
                            interpolator = OvershootInterpolator(1.2f)
                            start()
                        }
                        return false
                    }
                })
                .into(ivMain)

            // ── Labels ────────────────────────────────────────────────────
            when (category.lowercase()) {
                "letters" -> {
                    val letter = rawName.take(1).uppercase()
                    tvName.text      = letter
                    tvName.textSize  = 56f          // giant letter
                    tvName.setTextColor(Color.parseColor("#FFD700"))
                    tvSub.text       = "$letter is for $rawName"
                    tvSub.visibility = View.VISIBLE
                }
                else -> {
                    tvName.text      = rawName
                    tvName.textSize  = 28f
                    tvName.setTextColor(Color.WHITE)
                    tvSub.visibility = View.GONE
                }
            }

            // Slide-up entrance for label
            tvName.translationY = 40f
            tvName.alpha        = 0f
            tvName.animate()
                .translationY(0f).alpha(1f)
                .setDuration(400).setStartDelay(200)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // ── Swipe hint — fade after 2.5 s ────────────────────────────
            if (position == 0) {
                tvHint.visibility = View.VISIBLE
                tvHint.alpha      = 1f
                itemView.postDelayed({
                    tvHint.animate().alpha(0f).setDuration(700)
                        .withEndAction { tvHint.visibility = View.GONE }.start()
                }, 2_500)
            } else {
                tvHint.visibility = View.GONE
            }
        }
    }
}

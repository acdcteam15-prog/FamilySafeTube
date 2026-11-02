package com.example.familysafetube

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Pages of the vertical video pager. UI only: it owns NO player.
 *
 * A single ExoPlayer is created and owned by [OfflinePlayerActivity] and attached to whichever page
 * is currently visible ([VideoViewHolder.attachPlayer]). Exactly one player (one hardware decoder)
 * exists at any time, whichever direction the child swipes. The previous design created a player per
 * page and released the old one in page-index order, so swiping BACK briefly held two decoders
 * (the condition behind the earlier MediaCodec SIGABRT).
 *
 * Kept from earlier fixes: RESIZE_MODE_FIT (no cropping in landscape), re-layout after attaching the
 * player, tap-to-pause overlay.
 */
class VideoAdapter(
    private val videoPaths: List<String>,
    private val host: Host
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    /** Implemented by the activity that owns the shared player. */
    interface Host {
        fun onVideoTapped(holder: VideoViewHolder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val ctx = parent.context
        val dp = ctx.resources.displayMetrics.density

        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val playerView = PlayerView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        val spinner = ProgressBar(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt(), Gravity.CENTER)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4F8EF7"))
            visibility = View.GONE
        }

        val hudBg = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (72 * dp).toInt(), Gravity.BOTTOM
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val tvName = TextView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            ).also { it.marginStart = (16 * dp).toInt() }
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val tvCounter = TextView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.END
            ).also { it.marginEnd = (16 * dp).toInt() }
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 12f
        }

        val tvHint = TextView(ctx).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).also { it.topMargin = (32 * dp).toInt() }
            text = ctx.getString(R.string.video_swipe_hint)
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 13f
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#55000000"))
                cornerRadius = 50 * dp
            }
        }

        hudBg.addView(tvName)
        hudBg.addView(tvCounter)
        root.addView(playerView)
        root.addView(spinner)
        root.addView(hudBg)
        root.addView(tvHint)

        return VideoViewHolder(root, playerView, spinner, tvName, tvCounter, tvHint, host)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) =
        holder.bind(videoPaths[position], position, videoPaths.size)

    override fun getItemCount() = videoPaths.size

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.detachPlayer()          // the shared player must never stay attached to a recycled page
    }

    // ─────────────────────────────────────────────────────────────────────────
    class VideoViewHolder(
        itemView: View,
        private val playerView: PlayerView,
        private val spinner: ProgressBar,
        private val tvName: TextView,
        private val tvCounter: TextView,
        private val tvHint: TextView,
        host: Host
    ) : RecyclerView.ViewHolder(itemView) {

        /** Adapter position this page is currently bound to (-1 if none). */
        var boundPosition = -1
            private set

        // Pause overlay — big ▶ icon shown when the child taps to pause
        private val pauseOverlay: TextView = TextView(itemView.context).apply {
            text = "▶"
            textSize = 72f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = android.graphics.drawable.ColorDrawable(Color.parseColor("#55000000"))
            visibility = View.GONE
        }.also { (itemView as FrameLayout).addView(it) }

        init {
            playerView.setOnClickListener { host.onVideoTapped(this) }
        }

        fun bind(path: String, position: Int, total: Int) {
            boundPosition = position
            pauseOverlay.visibility = View.GONE
            spinner.visibility = View.GONE

            val file = File(path)
            tvName.text = "📹  ${file.nameWithoutExtension.substringAfter("_")
                .replace("_", " ").replaceFirstChar { it.uppercase() }}"
            tvCounter.text = "${position + 1} / $total"

            tvHint.visibility = View.VISIBLE
            tvHint.alpha = 1f
            itemView.postDelayed({
                tvHint.animate().alpha(0f).setDuration(800)
                    .withEndAction { tvHint.visibility = View.GONE }.start()
            }, 2_500)
        }

        /** Shows the shared player on this page. Re-layout: the view may still have a stale size. */
        fun attachPlayer(player: Player) {
            playerView.player = player
            playerView.requestLayout()
            playerView.invalidate()
        }

        fun detachPlayer() {
            playerView.player = null
            pauseOverlay.visibility = View.GONE
            spinner.visibility = View.GONE
        }

        fun showPaused(paused: Boolean) {
            pauseOverlay.visibility = if (paused) View.VISIBLE else View.GONE
        }

        fun showBuffering(buffering: Boolean) {
            spinner.visibility = if (buffering) View.VISIBLE else View.GONE
        }
    }
}

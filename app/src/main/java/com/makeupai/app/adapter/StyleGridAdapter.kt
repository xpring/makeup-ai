package com.makeupai.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.makeupai.app.R
import com.makeupai.app.model.MakeupStyle

class StyleGridAdapter(
    private val styles: List<MakeupStyle>,
    private val onStyleClick: (MakeupStyle, Int) -> Unit
) : RecyclerView.Adapter<StyleGridAdapter.StyleViewHolder>() {

    inner class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivResult: ImageView = itemView.findViewById(R.id.iv_result)
        val tvNameCn: TextView = itemView.findViewById(R.id.tv_name_cn)
        val tvNameEn: TextView = itemView.findViewById(R.id.tv_name_en)
        val tvCharacteristics: TextView = itemView.findViewById(R.id.tv_characteristics)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        val tvError: TextView = itemView.findViewById(R.id.tv_error)
        val vAccent: View = itemView.findViewById(R.id.v_accent)
        val containerOverlay: View = itemView.findViewById(R.id.container_overlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_makeup_style, parent, false)
        return StyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        val style = styles[position]

        holder.tvNameCn.text = style.nameCn
        holder.tvNameEn.text = style.nameEn
        holder.tvCharacteristics.text = style.characteristics

        // Accent color strip
        try {
            holder.vAccent.setBackgroundColor(Color.parseColor(style.accentColor))
        } catch (e: Exception) {
            holder.vAccent.setBackgroundColor(Color.parseColor("#F4A7B9"))
        }

        when (style.state) {
            MakeupStyle.State.PENDING -> {
                holder.progressBar.visibility = View.GONE
                holder.tvError.visibility = View.GONE
                holder.ivResult.visibility = View.VISIBLE
                holder.ivResult.setImageResource(R.drawable.ic_placeholder)
                holder.containerOverlay.visibility = View.VISIBLE
            }

            MakeupStyle.State.LOADING -> {
                holder.progressBar.visibility = View.VISIBLE
                holder.tvError.visibility = View.GONE
                holder.ivResult.visibility = View.VISIBLE
                holder.ivResult.setImageResource(R.drawable.ic_placeholder)
                holder.containerOverlay.visibility = View.VISIBLE
            }

            MakeupStyle.State.SUCCESS -> {
                holder.progressBar.visibility = View.GONE
                holder.tvError.visibility = View.GONE
                holder.ivResult.visibility = View.VISIBLE
                holder.ivResult.setImageBitmap(style.resultBitmap)
                holder.containerOverlay.visibility = View.VISIBLE

                holder.itemView.setOnClickListener {
                    onStyleClick(style, position)
                }
            }

            MakeupStyle.State.ERROR -> {
                holder.progressBar.visibility = View.GONE
                holder.tvError.visibility = View.VISIBLE
                holder.ivResult.visibility = View.VISIBLE
                holder.ivResult.setImageResource(R.drawable.ic_placeholder)
                holder.containerOverlay.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount() = styles.size

    fun updateStyle(index: Int) {
        notifyItemChanged(index)
    }
}

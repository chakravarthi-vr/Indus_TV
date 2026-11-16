package com.iptv.industv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(
    private val onClick: (Int) -> Unit,
    private val onLong: (Channel) -> Unit,
    private val isFav: (Channel) -> Boolean
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    var items: List<Channel> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val star: TextView = v.findViewById(R.id.star)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = items[i]
        h.name.text = c.name
        h.star.visibility = if (isFav(c)) View.VISIBLE else View.GONE
        h.logo.load(c.logo) {
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
            size(128)
        }
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) onClick(p)
        }
        h.itemView.setOnLongClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) { onLong(items[p]); notifyItemChanged(p) }
            true
        }
    }
}

class CategoryAdapter(private val onSelect: (String) -> Unit) : RecyclerView.Adapter<CategoryAdapter.VH>() {
    var items: List<String> = emptyList()
    var selected: String = "All"

    class VH(v: View) : RecyclerView.ViewHolder(v) { val t = v as TextView }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_chip, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        h.t.text = items[i]
        h.t.isSelected = items[i] == selected
        h.t.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) onSelect(items[p])
        }
    }
}

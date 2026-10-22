package dev.jzprice.livetv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** The channel grid. OK plays a channel, a long press on OK toggles its favorite star. */
class ChannelAdapter(
    private val favorites: Favorites,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    /** Re-binds what is on screen, e.g. after favorites changed in the player. */
    fun refreshAll() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        v.isFocusable = true
        v.isClickable = true
        v.isLongClickable = true
        v.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.star.visibility = if (favorites.isFavorite(c.key)) View.VISIBLE else View.GONE
        if (c.logo.isBlank()) {
            holder.logo.load(R.drawable.ic_placeholder)
        } else {
            holder.logo.load(c.logo) {
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }
        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        holder.itemView.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick(p)
            true
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val star: TextView = view.findViewById(R.id.star)
    }
}

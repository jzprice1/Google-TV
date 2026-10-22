package dev.jzprice.livetv

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Category(val id: String, val label: String)

/** The left-hand list: Favorites, All channels, then one entry per playlist group. */
class CategoryAdapter(
    private val onChosen: (String) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<Category> = emptyList()
    private var selectedId: String = ""

    fun submit(list: List<Category>, selected: String) {
        items = list
        selectedId = selected
        notifyDataSetChanged()
    }

    fun select(id: String) {
        val old = items.indexOfFirst { it.id == selectedId }
        selectedId = id
        val new = items.indexOfFirst { it.id == id }
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return VH(v as TextView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        holder.label.text = cat.label
        holder.label.isActivated = cat.id == selectedId
        holder.label.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                val chosen = items[p].id
                select(chosen)
                onChosen(chosen)
            }
        }
    }

    class VH(val label: TextView) : RecyclerView.ViewHolder(label)
}

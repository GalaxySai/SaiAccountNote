package com.galaxysai.saiaccountnote.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;


/**
 * 一个很小的通用 RecyclerView 适配器。
 *
 * <p>它不关心具体业务,只做三件事:</p>
 * <ul>
 *     <li>按 item 决定用哪个 layout;</li>
 *     <li>创建/复用 ViewHolder;</li>
 *     <li>把绑定工作交给调用方传入的 Binder。</li>
 * </ul>
 *
 * <p>首页的文件夹/账号搜索结果、账号页的账号行都用它。</p>
 */
public class SimpleAdapter<T> extends RecyclerView.Adapter<SimpleAdapter.Holder>
{
    /**
     * 根据 item 返回布局资源 id。
     * 返回的 layout id 同时作为 RecyclerView 的 viewType。
     */
    public interface LayoutProvider<T>
    {
        int layoutFor(T item);
    }

    /**
     * 把数据绑定到已经 inflate 出来的 itemView 上。
     */
    public interface Binder<T>
    {
        void bind(View itemView, T item, int position);
    }

    private final List<T> items = new ArrayList<>();
    private final LayoutProvider<T> layoutProvider;
    private final Binder<T> binder;

    public SimpleAdapter(LayoutProvider<T> layoutProvider, Binder<T> binder)
    {
        this.layoutProvider = layoutProvider;
        this.binder = binder;
    }

    /**
     * 替换数据并刷新。数据量不大,直接 notifyDataSetChanged 足够。
     */
    public void setItems(List<T> newItems)
    {
        items.clear();
        if (newItems != null)
        {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount()
    {
        return items.size();
    }

    @Override
    public int getItemViewType(int position)
    {
        return layoutProvider.layoutFor(items.get(position));
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position)
    {
        binder.bind(holder.itemView, items.get(position), position);
    }

    /**
     * 通用 ViewHolder:不持有具体控件,绑定逻辑由 Binder 完成。
     */
    public static class Holder extends RecyclerView.ViewHolder
    {
        public Holder(@NonNull View itemView)
        {
            super(itemView);
        }
    }
}

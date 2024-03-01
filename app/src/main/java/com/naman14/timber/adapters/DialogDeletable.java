package com.naman14.timber.adapters;

public interface DialogDeletable  {
    public void removeSongAt(int i);
    public void notifyItemRemoved(int position);
    public void notifyItemRangeChanged(int positionStart, int itemCount);
    public int getItemCount();
}

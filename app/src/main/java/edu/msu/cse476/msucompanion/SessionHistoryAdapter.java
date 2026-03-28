package edu.msu.cse476.msucompanion;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>{
    private List<WalkSession> sessions;

    private final SessionHistoryAdapter.OnItemClickListener listener;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView destinationName, status, startTime;

        ViewHolder(View itemView) {
            super(itemView);
            destinationName = itemView.findViewById(R.id.destinationName);
            status = itemView.findViewById(R.id.status);
            startTime = itemView.findViewById(R.id.startTime);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(WalkSession session);
    }

    public SessionHistoryAdapter(List<WalkSession> sessions, OnItemClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.session_history_item_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionHistoryAdapter.ViewHolder holder, int position) {
        WalkSession session = sessions.get(position);
        holder.destinationName.setText(session.getDestinationName());

        // Format the start time text
        Date startTime = session.getStartTime();
        if (startTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("E HH:mm MM-dd-yyyy", Locale.getDefault());
            String formattedStartTime = sdf.format(startTime);
            holder.startTime.setText(formattedStartTime);
        }

        // Format the status text
        String status = session.getStatus();
        String statusText = "Status:\n" + status.substring(0, 1).toUpperCase() + status.substring(1);
        holder.status.setText(statusText);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public void updateList(List<WalkSession> newList) {
        sessions = newList;
        notifyDataSetChanged();
    }
}

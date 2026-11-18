package com.builtbydeep.sovra;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

final class SovraSpinnerAdapter extends ArrayAdapter<String> {
    private final int textColor;
    private final int surfaceColor;

    SovraSpinnerAdapter(Context context, List<String> values) {
        super(context, android.R.layout.simple_spinner_item, values);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        textColor = context.getColor(R.color.sovra_text);
        surfaceColor = context.getColor(R.color.sovra_surface);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return style(super.getView(position, convertView, parent), false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return style(super.getDropDownView(position, convertView, parent), true);
    }

    private View style(View view, boolean dropDown) {
        if (!(view instanceof TextView)) {
            return view;
        }
        TextView text = (TextView) view;
        text.setTextColor(textColor);
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setMaxLines(1);
        if (dropDown) {
            text.setBackgroundColor(surfaceColor);
            int padding = Math.round(14 * text.getResources().getDisplayMetrics().density);
            text.setPadding(padding, padding, padding, padding);
        }
        return text;
    }
}

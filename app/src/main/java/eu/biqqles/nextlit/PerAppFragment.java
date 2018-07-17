
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerAppFragment extends Fragment {
    // A fragment which allows the user to configure notifications on a per-app basis.
    ApplicationAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final Context context = getContext();
        final View view = inflater.inflate(R.layout.fragment_per_app_config, container, false);
        final SwipeRefreshLayout swipeLayout = view.findViewById(R.id.swipeRefreshLayout);
        final RecyclerView recyclerView = view.findViewById(R.id.recyclerView);

        if (activity == null || context == null) {
            return view;
        }

        activity.setTitle(R.string.title_fragment_per_app_config);

        adapter = new ApplicationAdapter(context, swipeLayout);

        recyclerView.setAdapter(adapter);

        swipeLayout.setRefreshing(true);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.refresh();
            }
        });
        return view;
    }
}


class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.AppCard> {
    private List<AppInfo> apps = new ArrayList<>();
    private PackageManager pm;
    private SwipeRefreshLayout swipe;
    private SharedPreferences prefs;  // main app preferences
    private SharedPreferences appsEnabled;  // preferences store each app's status and pattern,
    private SharedPreferences appsPatterns;  // using package name as a key

    static class ParallelPackageLoader extends AsyncTask<ApplicationAdapter, Void, Void> {
        // This AsyncTask ensures that the RecycleView is populated asynchronously. This is required
        // because PackageManager takes a while to return data.
        private ApplicationAdapter adapter;

        @Override
        protected Void doInBackground(ApplicationAdapter... params) {
            adapter = params[0];  // 3 billion devices run Java
            List<ApplicationInfo> packages = adapter.pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo p : packages) {
                AppInfo app = new AppInfo();
                app.name = (String) p.loadLabel(adapter.pm);
                app.packageName = p.packageName;
                app.icon = p.loadIcon(adapter.pm);
                adapter.apps.add(app);
            }
            Collections.sort(adapter.apps);  // sort apps by name
            return null;
        }
        @Override
        protected void onPostExecute(Void param) {
            adapter.notifyDataSetChanged();  // update views using this adapter
            // ending the refresh animation here is hacky; this shouldn't need to be done in  an
            // adapter. However I can't see a reliable alternative
            adapter.swipe.setRefreshing(false);
        }
    }

    static class AppInfo implements Comparable<AppInfo> {
        String name = "";
        String packageName = "";
        Drawable icon;

        @Override
        public int compareTo(@NonNull AppInfo comparate) {
            // Instances of this class should be sorted by their name attributes
            return name.compareToIgnoreCase(comparate.name);
        }
    }

    static class AppCard extends RecyclerView.ViewHolder {
        static final long EXPAND_ANIMATION_DURATION = 240L;
        CardView card;
        ImageView icon;
        TextView name;
        TextView packageName;
        ToggleButton expand;
        LinearLayout configLayout;
        CheckBox enabled;
        Spinner pattern;

        AppCard(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardView);
            icon = itemView.findViewById(R.id.icon);
            name = itemView.findViewById(R.id.name);
            packageName = itemView.findViewById(R.id.packageName);
            expand = itemView.findViewById(R.id.expand);
            configLayout = itemView.findViewById(R.id.configLayout);
            enabled = itemView.findViewById(R.id.checkBox);
            pattern = itemView.findViewById(R.id.spinner);
        }
    }

    ApplicationAdapter(Context context, SwipeRefreshLayout swipe) {
        this.pm = context.getPackageManager();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.swipe = swipe;
        appsEnabled = context.getSharedPreferences("apps_enabled", Activity.MODE_PRIVATE);
        appsPatterns = context.getSharedPreferences("apps_patterns", Activity.MODE_PRIVATE);
        refresh();
    }

    public void refresh() {
        // Refresh the adapter.
        apps.clear();
        ParallelPackageLoader loader = new ParallelPackageLoader();
        loader.execute(this);
    }

    @Override
    public @NonNull AppCard onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        // Create card.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.per_app_card, parent, false);
        return new AppCard(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final AppCard card, int i) {
        // Populate card.
        final AppInfo app = apps.get(i);

        card.icon.setImageDrawable(app.icon);
        card.name.setText(app.name);
        card.packageName.setText(app.packageName);

        // "expand" button; shows configLayout
        card.expand.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                // animate button
                final float rotation = card.expand.getRotation();
                card.expand.animate().rotation(rotation == 0f ? 180f : 0f).setDuration(
                        AppCard.EXPAND_ANIMATION_DURATION).start();

                // show configLayout
                card.configLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
                if (checked) {
                    // on expansion, populate fields
                    final int defaultPattern = prefs.getInt("pattern", 1);

                    // "enabled" checkbox
                    card.enabled.setChecked(appsEnabled.getBoolean(app.packageName, true));
                    card.enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton button, boolean checked) {
                            appsEnabled.edit().putBoolean(app.packageName, checked).apply();
                            card.pattern.setEnabled(checked);
                        }
                    });

                    // pattern spinner
                    card.pattern.setSelection(appsPatterns.getInt(app.packageName, defaultPattern));
                    card.pattern.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        public void onItemSelected(AdapterView adapterView, View view, int i, long l) {
                            appsPatterns.edit().putInt(app.packageName, i).apply();
                        }

                        public void onNothingSelected(AdapterView<?> adapterView) { }
                    });
                }
            }
         });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }
}

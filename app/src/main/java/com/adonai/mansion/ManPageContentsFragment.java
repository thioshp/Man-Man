package com.adonai.mansion;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.adonai.mansion.entities.ManSectionItem;

import org.jsoup.helper.DataUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;


/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower that just search!
 *
 * @author Adonai
 */
public class ManPageContentsFragment extends Fragment {
    private final static String CHAPTER_INDEX = "chapter.index";

    private final static String CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com/";

    private RetrieveContentsCallback mContentRetrieveCallback = new RetrieveContentsCallback();
    private ChaptersArrayAdapter mChaptersAdapter;

    private Map<String, String> mCachedChapters;
    private Map<String, List<ManSectionItem>> mCachedChapterContents = new HashMap<>();

    private ListView mListView;
    private SmoothProgressBar mProgress;
    /**
     * Click listener for selecting a chapter from the list.
     * The request is then sent to the loader to load chapter data asynchronously
     * <br/>
     * We don't have any headers at this point
     *
     * @see com.adonai.mansion.ManPageContentsFragment.RetrieveContentsCallback
     */
    private AdapterView.OnItemClickListener mChapterClickListener = new AdapterView.OnItemClickListener() {

        @Override
        @SuppressWarnings("unchecked")
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Map.Entry<String, String> item = (Map.Entry<String, String>) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putString(CHAPTER_INDEX, item.getKey());
            getLoaderManager().restartLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, args, mContentRetrieveCallback);

            // show progressbar under actionbar
            mProgress.setIndeterminate(false);
            mProgress.progressiveStart();
            mProgress.setProgress(0);
            mProgress.setVisibility(View.VISIBLE);
        }
    };
    /**
     * Click listener for selecting a command from the list.
     * New instance of {@link com.adonai.mansion.ManPageDialogFragment} then created and shown
     * for loading ful command man page
     * <br/>
     * We have a header "To contents" so handle this case
     *
     */
    private AdapterView.OnItemClickListener mCommandClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            int headersCount = mListView.getHeaderViewsCount(); // always 1 in our case, "to contents" button
            if(position <= headersCount) { // header
                mListView.removeHeaderView(view);
                mListView.setAdapter(mChaptersAdapter);
                mListView.setOnItemClickListener(mChapterClickListener);
            } else {
                ManSectionItem item = (ManSectionItem) parent.getItemAtPosition(position + headersCount);
                ManPageDialogFragment.newInstance(item.getUrl()).show(getFragmentManager(), "manPage");
            }
        }
    };

    @NonNull
    public static ManPageContentsFragment newInstance() {
        ManPageContentsFragment fragment = new ManPageContentsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManPageContentsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, Bundle.EMPTY, mContentRetrieveCallback);
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mCachedChapters = Utils.parseStringArray(getActivity(), R.array.man_page_chapters);
        mChaptersAdapter = new ChaptersArrayAdapter(getActivity(), R.layout.chapters_list_item, R.id.chapter_index_label, new ArrayList<>(mCachedChapters.entrySet()));
        View root = inflater.inflate(R.layout.fragment_man_contents, container, false);

        mListView = (ListView) root.findViewById(R.id.chapter_commands_list);
        mListView.setAdapter(mChaptersAdapter);
        mListView.setOnItemClickListener(mChapterClickListener);
        mProgress = (SmoothProgressBar) getActivity().findViewById(R.id.load_progress);
        return root;
    }

    /**
     * This class represents an array adapter for showing man chapters
     * There are only about ten constant chapters, so it was convenient to place it to the string-array
     * <br/>
     * The array is retrieved via {@link Utils#parseStringArray(android.content.Context, int)}
     * and stored in {@link #mCachedChapters}
     */
    private class ChaptersArrayAdapter extends ArrayAdapter<Map.Entry<String, String>> {

        public ChaptersArrayAdapter(Context context, int resource, int textViewResourceId, List<Map.Entry<String, String>> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Map.Entry<String, String> current = getItem(position);
            View root = super.getView(position, convertView, parent);

            TextView index = (TextView) root.findViewById(R.id.chapter_index_label);
            index.setText(current.getKey());

            TextView name = (TextView) root.findViewById(R.id.chapter_name_label);
            name.setText(current.getValue());

            return root;
        }
    }

    /**
     * Array adapter for showing commands with their description in ListView
     * <br/>
     * The data retrieval is done through {@link com.adonai.mansion.ManPageContentsFragment.RetrieveContentsCallback}
     *
     * @see android.widget.ArrayAdapter
     * @see com.adonai.mansion.entities.ManSectionItem
     */
    private class ChapterContentsArrayAdapter extends ArrayAdapter<ManSectionItem> {

        public ChapterContentsArrayAdapter(Context context, int resource, int textViewResourceId, List<ManSectionItem> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ManSectionItem current = getItem(position);
            View root = super.getView(position, convertView, parent);

            TextView command = (TextView) root.findViewById(R.id.command_name_label);
            command.setText(current.getName());

            TextView desc = (TextView) root.findViewById(R.id.command_description_label);
            desc.setText(current.getDescription());

            return root;
        }
    }


    /**
     * Loader callback for async loading of clicked chapter's contents and showing them in ListView afterwards
     * <br/>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see com.adonai.mansion.entities.ManSectionItem
     */
    private class RetrieveContentsCallback implements LoaderManager.LoaderCallbacks<List<ManSectionItem>> {
        @Override
        public Loader<List<ManSectionItem>> onCreateLoader(int id, final Bundle args) {
            return new AsyncTaskLoader<List<ManSectionItem>>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                /**
                 * Loads chapter page from DB or network asynchronously
                 *
                 * @return list of commands with their descriptions and urls
                 * or null on error/no input provided
                 */
                @Nullable
                @Override
                public List<ManSectionItem> loadInBackground() {
                    if(args.containsKey(CHAPTER_INDEX)) { // retrieve chapter content
                        String index = args.getString(CHAPTER_INDEX);
                        String link = CHAPTER_COMMANDS_PREFIX + "/" + index;
                        args.remove(CHAPTER_INDEX);
                        try {
                            URLConnection conn = new URL(link).openConnection();
                            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                            conn.setReadTimeout(10000);
                            conn.setConnectTimeout(5000);
                            InputStream is = new GZIPInputStream(new CountingInputStream(conn.getInputStream(), conn.getContentLength()), conn.getContentLength());
                            Document root = DataUtil.load(is, "UTF-8", link);
                            Elements commands = root.select("div.e");
                            if(!commands.isEmpty()) {
                                List<ManSectionItem> msItems = new ArrayList<>(commands.size());
                                for(Element command : commands) {
                                    ManSectionItem msi = new ManSectionItem();
                                    msi.setName(command.child(0).text());
                                    msi.setUrl(CHAPTER_COMMANDS_PREFIX + command.child(0).attr("href"));
                                    msi.setDescription(command.child(2).text());
                                    msItems.add(msi);
                                }
                                return msItems;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            // can't show a toast from a thread without looper
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mProgress.setVisibility(View.INVISIBLE);
                                    Toast.makeText(getActivity(), R.string.connection_error, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<ManSectionItem>> loader, List<ManSectionItem> data) {
            mProgress.setVisibility(View.INVISIBLE);
            if(data != null) {
                View text = View.inflate(getActivity(), R.layout.back_header, null);
                mListView.addHeaderView(text);
                mListView.setAdapter(new ChapterContentsArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, data));
                mListView.setOnItemClickListener(mCommandClickListener);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<ManSectionItem>> loader) {

        }
    }

    private class CountingInputStream extends FilterInputStream {

        private final int length;
        private int transferred;

        CountingInputStream(InputStream in, int totalBytes) throws IOException {
            super(in);
            this.length = totalBytes;
            this.transferred = 0;
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int res = super.read(buffer, byteOffset, byteCount);
            transferred += res;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int progress = transferred * 100 / length;
                    mProgress.setProgress(progress);
                    if(progress == 100) {
                        mProgress.setIndeterminate(true);
                    }
                }
            });
            return res;
        }
    }
}

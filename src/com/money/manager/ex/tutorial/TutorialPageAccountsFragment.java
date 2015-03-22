package com.money.manager.ex.tutorial;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.app.Fragment;
import android.webkit.WebView;

import com.money.manager.ex.R;
import com.money.manager.ex.utils.RawFileUtils;

/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 */
public class TutorialPageAccountsFragment extends Fragment {

    public TutorialPageAccountsFragment() {
        // Required empty public constructor
    }

    public static TutorialPageAccountsFragment newInstance(){
        TutorialPageAccountsFragment fragment = new TutorialPageAccountsFragment();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_tutorial_page_accounts, container, false);

        // Load graphics.
        WebView webView = (WebView)view.findViewById(R.id.webViewAccounts);
        //webView.loadUrl("file:///android_asset/tutorial/accounts.html");

        // localization of the text.
        String content = RawFileUtils.getRawAsString(getActivity(), R.raw.tutorial_accounts);
        webView.loadData(content, "text/html", "UTF-8");

        return view;
    }

}

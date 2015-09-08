/*
 * Copyright (C) 2012-2015 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.money.manager.ex.investment;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.money.manager.ex.R;
import com.money.manager.ex.core.ExceptionHandler;
import com.money.manager.ex.core.NumericHelper;
import com.opencsv.CSVParser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Updates security prices from Yahoo Finance using YQL. All the work is done in the
 * background task.
 * References:
 * http://www.jarloo.com/yahoo_finance/
 */
public class YqlSecurityPriceUpdater
        implements ISecurityPriceUpdater, IDownloadAsyncTaskFeedback {

    public YqlSecurityPriceUpdater(Context context, IPriceUpdaterFeedback feedback) {
        mContext = context;
        mFeedback = feedback;
    }

    private Context mContext;
    private IPriceUpdaterFeedback mFeedback;
//    private String mSource = "yahoo.finance.quote";
    private final String mSource = "yahoo.finance.quotes";
    //
    private final String mBaseUri = "https://query.yahooapis.com/v1/public/yql";
    // https://query.yahooapis.com/v1/public/yql
    // ?q=... url escaped
    // &format=json
    // &diagnostics=true
    // &env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys
    // &callback=

    /**
     * Update prices for all the symbols in the list.
     */
    public void updatePrices(List<String> symbols) {
        if (symbols == null) return;

        // Get varargs from list.
        //String[] symbolsArray = symbols.toArray(new String[symbols.size()]);
        String url = getPriceUrl(symbols);

        // Download prices
        TextDownloaderTask downloader = new TextDownloaderTask(mContext, this);
        downloader.execute(url);

        // Async call. The prices are updated in onContentDownloaded.
    }

    @Override
    public String getUrlForSymbol(String symbol) {
        String result = getPriceUrl(Collections.singletonList(symbol));
        return result;
    }

    /**
     * Called from CSV downloader on progress update.
     * @param progress progress
     */
    @Override
    public void onProgressUpdate(String progress) {
        // progress is a number, percentage probably.
//        Log.d(LOGCAT, progress);
    }

    /**
     * Called from the Text Downloader when the file is downloaded and the contents read.
     * Here we have all the prices.
     * @param content The content received from the given url.
     */
    @Override
    public void onContentDownloaded(String content) {
        // validation
        if (TextUtils.isEmpty(content)) {
            throw new IllegalArgumentException("Downloadeded contents are empty");
        }

        // parse Json results
        List<SecurityPriceModel> pricesList = new ArrayList<>();
        try {
            pricesList = parseDownloadedContentJson(content);
        } catch (JSONException e) {
            ExceptionHandler handler = new ExceptionHandler(mContext, this);
            handler.handle(e, "parsing JSON");
        }

        for (SecurityPriceModel model : pricesList) {
            // Notify the caller by invoking the interface method.
            mFeedback.onPriceDownloaded(model.symbol, model.price, model.date);
        }
    }

    public String getYqlQueryFor(String source, List<String> fields, List<String> symbols) {
        // append quotes to all the symbols
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            symbol = "\"" + symbol + "\"";
            symbols.set(i, symbol);
        }

        String query = "select ";
        query += StringUtils.join(fields, ',');     // fields
        query += " from ";
        query += source;    // table
        query += " where symbol in (";
        query += StringUtils.join(symbols, ",");
        query += ")";

        return query;
    }

    private String getPriceUrl(List<String> symbols) {

        // http://stackoverflow.com/questions/1005073/initialization-of-an-arraylist-in-one-line
        List<String> fields = Arrays.asList("symbol", "LastTradePriceOnly", "LastTradeDate", "Currency");
        String query = getYqlQueryFor(mSource, fields, symbols);


        String uri = Uri.parse(mBaseUri)
                .buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("env", "store://datatables.org/alltableswithkeys")
                .build()
                .toString();

        return uri;
    }

    private List<SecurityPriceModel> parseDownloadedContentJson(String content) throws JSONException {
        ArrayList<SecurityPriceModel> result = new ArrayList<>();

        JSONObject root = new JSONObject(content);

        // check whether there is only one item or more
        Object quoteObject = root.getJSONObject("query").getJSONObject("results").get("quote");
        if (quoteObject instanceof JSONArray) {
            JSONArray quotes = root
                    .getJSONObject("query")
                    .getJSONObject("results")
                    .getJSONArray("quote");

            for (int i = 0; i < quotes.length(); i++) {
                JSONObject quote = quotes.optJSONObject(i);
                // process individual quote
                SecurityPriceModel priceModel = getSecurityPriceFor(quote);
                if (priceModel == null) continue;

                result.add(priceModel);
            }
        } else {
            // Single quote
            JSONObject quote = root
                    .getJSONObject("query")
                    .getJSONObject("results")
                    .getJSONObject("quote");

            SecurityPriceModel priceModel = getSecurityPriceFor(quote);
            if (priceModel != null) {
                result.add(priceModel);
            }
        }

        return result;
    }

    private SecurityPriceModel getSecurityPriceFor(JSONObject quote) throws JSONException {

        SecurityPriceModel priceModel = new SecurityPriceModel();
        priceModel.symbol = quote.getString("symbol");

        ExceptionHandler handler = new ExceptionHandler(mContext, this);

        // price
        String priceString = quote.getString("LastTradePriceOnly");
        if (!NumericHelper.isNumeric(priceString)) {
            handler.showMessage(mContext.getString(R.string.error_downloading_symbol) + " " +
                    priceModel.symbol);
            return null;
        }

        BigDecimal price = new BigDecimal(priceString);
        // LSE stocks are expressed in GBp (pence), not Pounds.
        // From stockspanel.cpp, line 785: if (StockQuoteCurrency == "GBp") dPrice = dPrice / 100;
        String currency = quote.getString("Currency");
        if (currency.equals("GBp")) {
            price = price.divide(BigDecimal.valueOf(100));
        }
        priceModel.price = price;

        // date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        Date date = null;
        try {
            date = dateFormat.parse(quote.getString("LastTradeDate"));
        } catch (ParseException e) {
            handler.handle(e, "parsing date from CSV");
        }
        priceModel.date = date;

        return priceModel;
    }
}

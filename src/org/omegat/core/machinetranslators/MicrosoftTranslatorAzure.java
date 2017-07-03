/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2012 Alex Buloichik, Didier Briel
               2016-2017 Aaron Madlon-Kay
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.machinetranslators;

import java.awt.Window;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.omegat.gui.exttrans.MTConfigDialog;
import org.omegat.util.Language;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.WikiGet;

/**
 * Support for Microsoft Translator API machine translation.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Didier Briel
 * @author Aaron Madlon-Kay
 *
 * @see <a href="https://www.microsoft.com/en-us/translator/translatorapi.aspx">Translator API</a>
 * @see <a href="https://docs.microsofttranslator.com/text-translate.html">Translate Method reference</a>
 */
public class MicrosoftTranslatorAzure extends BaseTranslate {
    private static final Logger LOGGER = Logger.getLogger(MicrosoftTranslatorAzure.class.getName());

    protected String PROPERTY_SUBSCRIPTION_KEY = "microsoft.api.subscription_key";
    
    protected String PROPERTY_CATEGORY_KEY = "microsoft.api.category_key";;
        
    protected static final String URL_TOKEN = "https://api.cognitive.microsoft.com/sts/v1.0/issueToken";
    protected static final String URL_TRANSLATE = "https://api.microsofttranslator.com/v2/http.svc/Translate";
    protected static final Pattern RE_RESPONSE = Pattern.compile("<string[^>]*>(.+)</string>");

    protected String accessToken;
    
    @Override
    protected String getPreferenceName() {
        return Preferences.ALLOW_MICROSOFT_TRANSLATOR_AZURE;
    }

    public String getName() {
        return OStrings.getString("MT_ENGINE_MICROSOFT_AZURE");
    }

    @Override
    protected synchronized String translate(Language sLang, Language tLang, String text) throws Exception {
        text = text.length() > 10000 ? text.substring(0, 9997) + "..." : text;
        String prev = getFromCache(sLang, tLang, text);
        if (prev != null) {
            return prev;
        }

        String langFrom = checkMSLang(sLang);
        String langTo = checkMSLang(tLang);
        try {
            String translation;
            if (accessToken == null) {
                requestToken();
                translation = requestTranslate(langFrom, langTo, text);
            } else {
                try {
                    translation = requestTranslate(langFrom, langTo, text);
                } catch (WikiGet.ResponseError ex) {
                    if (ex.code == 400) {
                        LOGGER.finer("Re-fetching Microsoft Translator API token due to 400 response");
                        requestToken();
                        translation = requestTranslate(langFrom, langTo, text);
                    } else {
                        throw ex;
                    }
                }
            }
            if (translation != null) {
                putToCache(sLang, tLang, text, translation);
            }
            return translation;
        } catch (WikiGet.ResponseError ex) {
            return ex.getLocalizedMessage();
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, ex.getLocalizedMessage(), ex);
            return ex.getLocalizedMessage();
        }
    }

    /**
     * Converts language codes to Microsoft ones.
     * @param language
     *              a project language
     * @return either a language code, or a Chinese language code plus a Microsoft variant
     */
    private String checkMSLang(Language language) {
        String lang = language.getLanguage();
        if (lang.equalsIgnoreCase("zh-cn")) {
            return "zh-CHS";
        } else if (lang.equalsIgnoreCase("zh-tw") || lang.equalsIgnoreCase("zh-hk")) {
            return "zh-CHT";
        } else {
            return language.getLanguageCode();
        }

    }
    private void requestToken() throws Exception {
        String key = getCredential(PROPERTY_SUBSCRIPTION_KEY);
        if (StringUtil.isEmpty(key)) {
            throw new Exception(OStrings.getString("MT_ENGINE_MICROSOFT_SUBSCRIPTION_KEY_NOTFOUND"));
        }
        Map<String, String> headers = new TreeMap<String, String>();
        headers.put("Ocp-Apim-Subscription-Key", key);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/jwt");
        accessToken = WikiGet.post(URL_TOKEN, Collections.emptyMap(), headers);
    }

    private String requestTranslate(String langFrom, String langTo, String text) throws Exception {
        String category = getCredential(PROPERTY_CATEGORY_KEY);
        Map<String, String> p = new TreeMap<String, String>();
        p.put("appid", "Bearer " + accessToken);
        p.put("text", text);
        p.put("from", langFrom);
        p.put("to", langTo);
        p.put("category", category);
        p.put("contentType", "text/plain");

        String r = WikiGet.get(URL_TRANSLATE, p, null);
        Matcher m = RE_RESPONSE.matcher(r);
        if (m.matches()) {
            String translatedText = m.group(1);
            translatedText = translatedText.replace("&lt;", "<");
            translatedText = translatedText.replace("&gt;", ">");
            return translatedText;
        } else {
            LOGGER.warning(OStrings.getString("MT_ENGINE_MICROSOFT_WRONG_RESPONSE"));
            return null;
        }                                                     
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void showConfigurationUI(Window parent) {
        MTConfigDialog dialog = new MTConfigDialog(parent, getName()) {
            @Override
            protected void onConfirm() {
                String key = panel.valueField1.getText().trim();
                String category = panel.valueField2.getText().trim();
                boolean temporary = panel.temporaryCheckBox.isSelected();
                setCredential(PROPERTY_SUBSCRIPTION_KEY, key, temporary);
                setCredential(PROPERTY_CATEGORY_KEY, category, temporary);
            }
        };
        dialog.panel.valueLabel1.setText(OStrings.getString("MT_ENGINE_MICROSOFT_SUBSCRIPTION_KEY_LABEL"));
        dialog.panel.valueField1.setText(getCredential(PROPERTY_SUBSCRIPTION_KEY));
        dialog.panel.valueLabel2.setText(OStrings.getString("MT_ENGINE_MICROSOFT_CATEGORY_KEY_LABEL"));
        dialog.panel.valueField2.setText(getCredential(PROPERTY_CATEGORY_KEY));
        dialog.panel.temporaryCheckBox.setSelected(isCredentialStoredTemporarily(PROPERTY_SUBSCRIPTION_KEY));
        dialog.show();
    }
}

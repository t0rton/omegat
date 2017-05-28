/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2010 Alex Buloichik, Ibai Lakunza Velasco, Didier Briel
               2013 Martin Wunderlich
               2015 Didier Briel
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

import java.util.Map;

import org.omegat.util.Language;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;

/**
 * @author Ibai Lakunza Velasco
 * @author Didier Briel
 * @author Martin Wunderlich
 * @author Briac Pilpre
 */
public final class MyMemoryHumanTranslate extends AbstractMyMemoryTranslate {

    @Override
    protected String getPreferenceName() {
        return Preferences.ALLOW_MYMEMORY_HUMAN_TRANSLATE;
    }

    @Override
    public String getName() {
        return OStrings.getString("MT_ENGINE_MYMEMORY_HUMAN");
    }

    @Override
    protected boolean includeMT() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        String prev = getFromCache(sLang, tLang, text);
        if (prev != null) {
            return prev;
        }

        Map<String, Object> jsonResponse;

        // Get MyMemory response in JSON format
        try {
            jsonResponse = (Map<String, Object>) getMyMemoryResponse(sLang, tLang, text);
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }

        String translation = "";
        try {
            // responseData/translatedText contains the best match.
            Map<String, Object> dataNode = (Map<String, Object>) jsonResponse.get("responseData");
            translation = dataNode.get("translatedText").toString();
        } catch (NullPointerException e) {
            return null;
        }

        putToCache(sLang, tLang, text, translation);
        return translation;
    }

}

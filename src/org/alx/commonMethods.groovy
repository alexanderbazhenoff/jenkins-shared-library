package org.alx


/**
 * Jenkins shared library. Written by Aleksandr Bazhenov, 2021-2024.
 * Common functions to use in jenkins pipelines.
 *
 * This Source Code Form is subject to the terms of the Apache License v2.0.
 * If a copy of this source file was not distributed with this file, You can obtain one at:
 * https://github.com/alexanderbazhenoff/jenkins-shared-library/blob/main/LICENSE
 */

/**
 * Apply ReplaceAll regex items to string.
 *
 * @param text - text to process.
 * @param regexItemsList - list of regex items to apply .replaceAll method.
 * @param replaceItemsList - list of items to replace with. List must be the same length as a regexItemsList, otherwise
 *                           will be replaced with empty line ''.
 * @return - resulting text.
 */
static String applyReplaceRegexItems(String text, List regexItemsList, List replaceItemsList = []) {
    String replacedText = text
    regexItemsList.eachWithIndex { value, Integer index ->
        replacedText = replacedText.replaceAll(value as CharSequence,
                replaceItemsList[index] ? replaceItemsList[index] as String : '')
    }
    replacedText
}

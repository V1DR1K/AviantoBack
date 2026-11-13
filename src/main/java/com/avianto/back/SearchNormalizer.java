package com.avianto.back;

import java.text.Normalizer;
import java.util.Locale;

final class SearchNormalizer {
  private SearchNormalizer() {}

  static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "")
      .toLowerCase(Locale.ROOT)
      .replaceAll("\\s+", " ")
      .trim();
  }

  static String likePattern(String value) {
    return "%" + normalize(value) + "%";
  }

  static String jpql(String property) {
    return "function('unaccent', lower(" + property + "))";
  }
}

/*
 * MIT License
 *
 * Copyright (c) 2022 BlvckBytes
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.blvckbytes.gpeee.tokenizer;

import me.blvckbytes.gpeee.error.UnterminatedStringError;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

public enum TokenType {

  //=========================================================================//
  //                                 Literals                                //
  //=========================================================================//

  TRUE(TokenCategory.LITERAL, "true", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "true".toCharArray())),
  FALSE(TokenCategory.LITERAL, "false", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "false".toCharArray())),
  NULL(TokenCategory.LITERAL, "null", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "null".toCharArray())),

  //=========================================================================//
  //                                  Values                                 //
  //=========================================================================//

  IDENTIFIER(TokenCategory.VALUE, null, tokenizer -> {
    StringBuilder result = new StringBuilder();

    char firstChar = tokenizer.nextChar();

    // Identifiers always start with letters
    if (!isIdentifierChar(firstChar, true))
      return null;

    result.append(firstChar);

    // Collect until no more identifier chars remain
    while (tokenizer.hasNextChar() && isIdentifierChar(tokenizer.peekNextChar(), false))
      result.append(tokenizer.nextChar());

    return result.toString();
  }),

  // -?[0-9]+
  LONG(TokenCategory.VALUE, null, tokenizer -> {
    StringBuilder result = new StringBuilder();

    if (collectDigits(tokenizer, result, false) != CollectorResult.READ_OKAY)
      return null;

    return result.toString();
  }),

  // -?[0-9]*.?[0-9]+
  DOUBLE(TokenCategory.VALUE, null, tokenizer -> {
    StringBuilder result = new StringBuilder();

    // Shorthand 0.x notation
    if (tokenizer.peekNextChar() == '.') {
      result.append('0');
      result.append(tokenizer.nextChar());

      // Collect as many digits as possible
      if (collectDigits(tokenizer, result, false) != CollectorResult.READ_OKAY)
        return null;

      return result.toString();
    }

    // A double starts out like an integer
    if (collectDigits(tokenizer, result, true) != CollectorResult.READ_OKAY)
      return null;

    // Missing decimal point
    if (!tokenizer.hasNextChar() || tokenizer.nextChar() != '.')
      return null;

    result.append('.');

    // Collect as many digits as possible
    if (collectDigits(tokenizer, result, false) != CollectorResult.READ_OKAY)
      return null;

    return result.toString();
  }),

  STRING(TokenCategory.VALUE, null, tokenizer -> {
    int startRow = tokenizer.getCurrentRow(), startCol = tokenizer.getCurrentCol();

    // String start marker not found
    if (tokenizer.nextChar() != '"')
      return null;

    StringBuilder result = new StringBuilder();

    boolean isTerminated = false;
    while (tokenizer.hasNextChar()) {
      char c = tokenizer.nextChar();

      @Nullable Character previous = result.length() == 0 ? null : result.charAt(result.length() - 1);
      @Nullable Character previousPrevious = result.length() <= 1 ? null : result.charAt(result.length() - 2);

      // Delete an escaped escape sequence once to just leave the escape symbol
      if (previous != null && previous == '\\' && previousPrevious != null && previousPrevious == '\\')
        result.deleteCharAt(result.length() - 1);

      if (c == '"') {
        // Escaped double quote character, remove leading backslash
        if (previous != null && previous == '\\' && (previousPrevious == null || previousPrevious != '\\')) {
          result.deleteCharAt(result.length() - 1);
          result.append(c);
          continue;
        }

        isTerminated = true;
        break;
      }

      if (c == 's') {
        // Escaped s character, substitute for single quote
        if (previous != null && previous == '\\' && (previousPrevious == null || previousPrevious != '\\')) {
          result.deleteCharAt(result.length() - 1);
          result.append('\'');
          continue;
        }
      }

      result.append(c);
    }

    // Strings need to be terminated
    if (!isTerminated)
      throw new UnterminatedStringError(startRow, startCol, tokenizer.getRawText());

    return result.toString();
  }),

  //=========================================================================//
  //                                Operators                                //
  //=========================================================================//

  EXPONENT(TokenCategory.OPERATOR, "^", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '^')),
  MULTIPLICATION(TokenCategory.OPERATOR, "*", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '*')),
  DIVISION(TokenCategory.OPERATOR, "/", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '/')),
  MODULO(TokenCategory.OPERATOR, "%", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '%')),
  PLUS(TokenCategory.OPERATOR, "+", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '+')),
  MINUS(TokenCategory.OPERATOR, "-", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '>', '-')),

  GREATER_THAN(TokenCategory.OPERATOR, ">", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '=', '>')),
  GREATER_THAN_OR_EQUAL(TokenCategory.OPERATOR, ">=", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '>', '=')),
  LESS_THAN(TokenCategory.OPERATOR, "<", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '=', '<')),
  LESS_THAN_OR_EQUAL(TokenCategory.OPERATOR, "<=", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '<', '=')),
  VALUE_EQUALS(TokenCategory.OPERATOR, "==", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '=', '=', '=')),
  VALUE_NOT_EQUALS(TokenCategory.OPERATOR, "!=", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '=', '!', '=')),
  VALUE_EQUALS_EXACT(TokenCategory.OPERATOR, "===", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '=', '=', '=')),
  VALUE_NOT_EQUALS_EXACT(TokenCategory.OPERATOR, "!==", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '!', '=', '=')),

  CONCATENATE(TokenCategory.OPERATOR, "&", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '&')),

  BOOL_NOT(TokenCategory.KEYWORD, "not", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "not".toCharArray())),
  BOOL_AND(TokenCategory.KEYWORD, "and", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "and".toCharArray())),
  BOOL_OR(TokenCategory.KEYWORD, "or", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "or".toCharArray())),
  KW_IF(TokenCategory.KEYWORD, "if", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "if".toCharArray())),
  KW_THEN(TokenCategory.KEYWORD, "then", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "then".toCharArray())),
  KW_ELSE(TokenCategory.KEYWORD, "else", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "else".toCharArray())),

  ARROW(TokenCategory.OPERATOR, "=>", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "=>".toCharArray())),
  ASSIGN(TokenCategory.OPERATOR, "=", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, c -> c == '=', '=')),
  NULL_COALESCE(TokenCategory.OPERATOR, "??", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, "??".toCharArray())),

  //=========================================================================//
  //                                 Symbols                                 //
  //=========================================================================//

  PARENTHESIS_OPEN(TokenCategory.SYMBOL, "(", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '(')),
  OPTIONAL_PARENTHESIS_OPEN(TokenCategory.SYMBOL, "?(", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '?', '(')),
  PARENTHESIS_CLOSE(TokenCategory.SYMBOL, ")", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, ')')),
  COMMA(TokenCategory.SYMBOL, ",", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, ',')),
  DOT(TokenCategory.SYMBOL, ".", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, TokenType::isDigit, '.')),
  OPTIONAL_DOT(TokenCategory.SYMBOL, "?.", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, TokenType::isDigit, '?', '.')),
  BRACKET_OPEN(TokenCategory.SYMBOL, "[", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '[')),
  OPTIONAL_BRACKET_OPEN(TokenCategory.SYMBOL, "?[", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, '?', '[')),
  BRACKET_CLOSE(TokenCategory.SYMBOL, "]", tokenizer -> tryCollectSequenceWithNextCheck(tokenizer, null, ']')),

  //=========================================================================//
  //                                Invisible                                //
  //=========================================================================//

  COMMENT(TokenCategory.INVISIBLE, null, tokenizer -> {
    StringBuilder result = new StringBuilder();

    if (tokenizer.nextChar() != '#')
      return null;

    while (tokenizer.hasNextChar() && tokenizer.peekNextChar() != '\n')
      result.append(tokenizer.nextChar());

    return result.toString();
  }),
  ;

  private final TokenCategory category;
  private final String representation;
  private final FTokenReader tokenReader;

  public static final TokenType[] valuesInTrialOrder;
  public static final TokenType[] nonNumericTypes;
  public static final TokenType[] valueTypes;

  static {
    valuesInTrialOrder =
      Arrays.stream(values())
      .sorted(Comparator.comparingInt(tt -> tt.getCategory().ordinal()))
      .toArray(TokenType[]::new);

    nonNumericTypes = Arrays.stream(values())
      .filter(type -> type != LONG && type != DOUBLE)
      .toArray(TokenType[]::new);

    valueTypes = Arrays.stream(values())
      .filter(type -> type.getCategory() == TokenCategory.VALUE)
      .toArray(TokenType[]::new);
  }

  TokenType(TokenCategory category, String representation, FTokenReader tokenReader) {
    this.category = category;
    this.representation = representation;

    // Extract out initially checking for EOF for all readers
    this.tokenReader = tokenizer -> {

      if (!tokenizer.hasNextChar())
        return null;

      return tokenReader.apply(tokenizer);
    };
  }

  public TokenCategory getCategory() {
    return category;
  }

  public String getRepresentation() {
    return representation;
  }

  public FTokenReader getTokenReader() {
    return tokenReader;
  }

  private static CollectorResult collectDigits(ITokenizer tokenizer, StringBuilder result, boolean stopBeforeDot) {
    if (!tokenizer.hasNextChar())
      return CollectorResult.NO_NEXT_CHAR;

    int initialLength = result.length();

    while (tokenizer.hasNextChar()) {
      char c = tokenizer.nextChar();

      // Collect as many digits as possible
      if (isDigit(c))
        result.append(c);

      // Whitespace or newline stops the number notation
      else if (tokenizer.isConsideredWhitespace(c) || c == '\n')
        break;

      else if (c == '.' && stopBeforeDot) {
        tokenizer.undoNextChar();
        break;
      }

      else {
        tokenizer.undoNextChar();

        if (result.length() - initialLength > 0 && wouldFollow(tokenizer, nonNumericTypes))
          return CollectorResult.READ_OKAY;

        return CollectorResult.CHAR_MISMATCH;
      }
    }

    return result.length() > 0 ? CollectorResult.READ_OKAY : CollectorResult.CHAR_MISMATCH;
  }

  private static @Nullable String tryCollectSequenceWithNextCheck(ITokenizer tokenizer, @Nullable Function<Character, Boolean> notNextCheck, char... sequence) {
    StringBuilder result = new StringBuilder();

    if (collectSequence(tokenizer, result, sequence) != CollectorResult.READ_OKAY)
      return null;

    if (notNextCheck != null && tokenizer.hasNextChar() && notNextCheck.apply(tokenizer.peekNextChar()))
      return null;

    return result.toString();
  }

  private static char charToLowerCase(char input) {
    // Upper case character, convert to lowercase by shifting over 32 places
    if (input >= 'A' && input <= 'Z')
      input += 32;
    return input;
  }

  private static CollectorResult collectSequence(ITokenizer tokenizer, StringBuilder result, char... sequence) {
    for (char c : sequence) {
      if (!tokenizer.hasNextChar())
        return CollectorResult.NO_NEXT_CHAR;

      if (charToLowerCase(tokenizer.nextChar()) == charToLowerCase(c)) {
        result.append(c);
        continue;
      }

      return CollectorResult.CHAR_MISMATCH;
    }

    return CollectorResult.READ_OKAY;
  }

  private static boolean wouldFollow(ITokenizer tokenizer, TokenType... types) {
    for (TokenType type : types) {
      FTokenReader reader = type.getTokenReader();

      // Non-implemented tokens cannot follow
      if (reader == null)
        continue;

      // Simulate a token read trial
      tokenizer.saveState(false);
      boolean success = type.getTokenReader().apply(tokenizer) != null;
      tokenizer.restoreState(false);

      if (success)
        return true;
    }

    // None matched
    return false;
  }

  /**
   * Checks whether a given character is within the range of allowed
   * characters to make up an identifier token
   * @param c Character in question
   * @param isFirst Whether it's the first char of the token (special rules apply)
   * @return True if allowed, false otherwise
   */
  private static boolean isIdentifierChar(char c, boolean isFirst) {
    return (
      (c >= 'a' && c <= 'z') ||
      (c >= 'A' && c <= 'Z') ||
      // Underscores as well as numbers aren't allowed as the first character
      (!isFirst && (c == '_' || isDigit(c)))
    );
  }

  /**
   * Checks whether the provided character is a digit
   * @param c Character in question
   */
  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}

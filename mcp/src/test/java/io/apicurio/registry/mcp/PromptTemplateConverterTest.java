/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the {{variable}} substitution done by the MCP prompt converter. These do not need a
 * Quarkus context - the converter only needs its ObjectMapper, which is set directly here.
 */
class PromptTemplateConverterTest {

    private PromptTemplateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PromptTemplateConverter();
        converter.jsonMapper = new ObjectMapper();
    }

    @Test
    void testRenderPlainVariable() {
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{name}}", Map.of("name", "Alice")));
    }

    @Test
    void testRenderVariableWithWhitespace() {
        // Previously the converter substituted the literal string "{{" + key + "}}", so a
        // space-padded placeholder was left in the output as dead text.
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{ name }}", Map.of("name", "Alice")));
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{   name   }}", Map.of("name", "Alice")));
    }

    @Test
    void testRenderMixedSpellingsOfSameVariable() {
        Assertions.assertEquals("Alice / Alice",
                converter.renderTemplate("{{name}} / {{ name }}", Map.of("name", "Alice")));
    }

    @Test
    void testUnknownVariableKeepsPlaceholder() {
        Assertions.assertEquals("Hello {{name}}",
                converter.renderTemplate("Hello {{name}}", Map.of()));
    }

    @Test
    void testNullValueRendersAsEmptyString() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", null);
        Assertions.assertEquals("Hello !", converter.renderTemplate("Hello {{name}}!", args));
    }

    @Test
    void testNullArgsReturnsTemplateUnchanged() {
        Assertions.assertEquals("Hello {{name}}", converter.renderTemplate("Hello {{name}}", null));
    }

    @Test
    void testValueWithRegexReplacementCharacters() {
        Assertions.assertEquals("Cost: $1,000",
                converter.renderTemplate("Cost: {{amount}}", Map.of("amount", "$1,000")));
    }

    @Test
    void testVariableNameWithRegexMetacharacterDoesNotMatchWildly() {
        // The old code built a regex from the argument name, so a '.' in the key matched any
        // character. "a.b" must not substitute the unrelated placeholder "{{axb}}".
        Assertions.assertEquals("{{axb}}",
                converter.renderTemplate("{{axb}}", Map.of("a.b", "boom")));
    }

    /**
     * Argument names are supplied by the caller and are not validated against the template's
     * declared variables, so they must never be treated as a regular expression. An argument
     * named <code>.*</code> must not match placeholders it does not name.
     */
    @Test
    void testArgumentNameWithRegexMetacharactersLeavesOtherPlaceholdersIntact() {
        String rendered = converter.renderTemplate("Review {{code}} focusing on {{focus_area}}.",
                Map.of(".*", "INJECTED"));

        Assertions.assertEquals("Review {{code}} focusing on {{focus_area}}.", rendered);
    }

    /**
     * An argument name that is not a valid regular expression must not reach a Pattern, so that
     * it cannot fail the render with a PatternSyntaxException.
     */
    @Test
    void testArgumentNameThatIsNotValidRegexDoesNotFailTheRender() {
        String rendered = converter.renderTemplate("Write in a {{tone}} tone.",
                Map.of("a)(b", "x"));

        Assertions.assertEquals("Write in a {{tone}} tone.", rendered);
    }

    @Test
    void testConditionalBlockStillWorks() {
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("{{#if premium}}Hello {{ name }}{{/if}}",
                        Map.of("premium", true, "name", "Alice")));
    }

    @Test
    void testConditionalBlockDroppedWhenFalsy() {
        Assertions.assertEquals("",
                converter.renderTemplate("{{#if premium}}Hello {{ name }}{{/if}}",
                        Map.of("premium", false, "name", "Alice")));
    }

    @Test
    void testParseAndRenderAutoDetectJsonWithWhitespaceVariable() {
        String json = """
                {
                    "templateId": "greeting",
                    "template": "Hello {{ name }}!",
                    "variables": { "name": { "type": "string" } }
                }
                """;
        Assertions.assertEquals("Hello Alice!",
                converter.parseAndRenderAutoDetect(json, Map.of("name", "Alice")));
    }
}

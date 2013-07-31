package org.dynjs.runtime.builtins.types.string.prototype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dynjs.runtime.*;
import org.dynjs.runtime.builtins.types.regexp.DynRegExp;
import org.joni.Region;

public class Replace extends AbstractNativeFunction {

    public Replace(GlobalObject globalObject) {
        super(globalObject, "searchValue", "replaceValue");
    }

    @Override
    public Object call(ExecutionContext context, Object self, Object... args) {
        Types.checkObjectCoercible(context, self);
        final String searchString = Types.toString(context, self);
        JSFunction replacement;
        if(args[1] instanceof JSFunction){
            replacement = (JSFunction) args[1];
        }else{
            replacement = new IdentityFunction(context.getGlobalObject(), args[1]);
        }

        if(args[0] instanceof DynRegExp){
            return replaceWithRegex(context, searchString, (DynRegExp)args[0], replacement);
        }else{
            return replaceWithString(context, searchString, Types.toString(context, args[0]), replacement);
        }
    }

    private Object replaceWithString(ExecutionContext context, String searchString, String query, JSFunction function) {
        int index = searchString.indexOf(query);
        if(index < 0) { return searchString; }

        Object[] fnArgs = new Object[3];
        fnArgs[0] = searchString.substring(index, index + query.length());
        fnArgs[1] = index;
        fnArgs[2] = searchString;

        String replacement = buildReplacementString(
                searchString,
                Types.toString(context, context.call(function, Types.UNDEFINED, fnArgs)),
                Arrays.asList(new Match(searchString, index, index + query.length())));

        return searchString.replaceFirst(Pattern.quote(query), Matcher.quoteReplacement(replacement));
    }

    private Object replaceWithRegex(ExecutionContext context, String searchString, DynRegExp regexp, JSFunction function) {
        final StringBuilder result = new StringBuilder();

        Region region;
        Region lastRegion = null;
        int startIndex = 0;
        while((region = regexp.match(searchString, startIndex)) != null){
            lastRegion = region;

            Object[] fnArgs = new Object[3 + region.numRegs];
            fnArgs[1 + region.numRegs] = region.beg[0];
            fnArgs[2 + region.numRegs] = searchString;
            List<Match> matches = new ArrayList<>();
            for(int i = 0; i < region.numRegs; i++){
                matches.add(new Match(searchString, region.beg[i], region.end[i]));
                String substring = searchString.substring(region.beg[i], Math.min(searchString.length(), region.end[i]));
                fnArgs[i] = substring;
            }

            String replacement = buildReplacementString(searchString,
                    Types.toString(context, context.call(function, Types.UNDEFINED, fnArgs)), matches);
            result.append(searchString.substring(startIndex, region.beg[0]))
                    .append(replacement);

            if(startIndex == region.end[0]){
                result.append(searchString.substring(startIndex, Math.min(startIndex + 1, searchString.length())));
                startIndex++;
            }else{
                startIndex = region.end[0];
            }

            if(!regexp.isGlobal()){
                break;
            }
        }
        if(lastRegion != null){
            result.append(searchString.substring(Math.min(searchString.length(), lastRegion.end[0])));
        }else{
            result.append(searchString);
        }

        return result.toString();
    }

    private static class IdentityFunction extends AbstractNativeFunction{
        private final Object value;

        public IdentityFunction(GlobalObject globalObject, Object value) {
            super(globalObject);
            this.value = value;
        }

        @Override
        public Object call(ExecutionContext context, Object self, Object... args) {
            return value;
        }
    }

    protected String buildReplacementString(String original, String replaceWith, List<Match> matches) {
        int fromIndex = 0;
        int endIndex = replaceWith.length() - 1;
        StringBuilder replacement = new StringBuilder();

        while (fromIndex <= endIndex) {
            char nextChar;
            if ((nextChar = replaceWith.charAt(fromIndex)) == '$' && fromIndex != endIndex) {
                switch (replaceWith.charAt(fromIndex + 1)) {
                    case '$':
                        replacement.append("$");
                        fromIndex++;
                        break;
                    case '&':
                        replacement.append(matches.get(0).capture());
                        fromIndex++;
                        break;
                    case '`':
                        replacement.append(original.substring(0, matches.get(0).start));
                        fromIndex++;
                        break;
                    case '\'':
                        replacement.append(original.substring(matches.get(0).end));
                        fromIndex++;
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        Integer backreference = Integer.parseInt(String.valueOf(replaceWith.charAt(fromIndex + 1)));
                        if (backreference < matches.size()) {
                            replacement.append(matches.get(backreference).capture());
                            fromIndex++;
                        } else {
                            replacement.append(nextChar);
                        }
                        break;
                    default:
                        replacement.append(nextChar);
                }
            } else {
                replacement.append(nextChar);
            }
            fromIndex++;
        }
        return replacement.toString();
    }

    private static class Match{
        private final String original;
        private final int start;
        private final int end;

        public Match(String original, int start, int end){
            this.original = original;
            this.start = start;
            this.end = end;
        }

        public String capture() {
            return original.substring(start, end);
        }
    }
}

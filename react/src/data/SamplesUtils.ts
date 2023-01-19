import GrammarSample from "./GrammarSample";
import { SAMPLE_GRAMMAR } from "./Samples";

const GRAMMAR_INDEX_URL = "https://raw.githubusercontent.com/antlr/grammars-v4/master/grammars.json";

function fetchGrammarSamples(onSuccess: (samples: GrammarSample[]) => void, onError: (reason: any) => void): void  {
    fetch(GRAMMAR_INDEX_URL)
        .then(async response => {
            let samples: GrammarSample[] = await response.json();
            samples = samples.sort( (s1, s2) => s1.name.toLowerCase().localeCompare(s2.name.toLowerCase(), 'en'));
            samples.unshift(SAMPLE_GRAMMAR);
            onSuccess(samples);
        })
        .catch(reason => {
            onError(reason);
        });
}

export { fetchGrammarSamples }

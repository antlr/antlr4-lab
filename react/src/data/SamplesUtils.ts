import GrammarSample from "./GrammarSample";
import { SAMPLE_GRAMMAR } from "./Samples";

const GRAMMAR_INDEX_URL = "https://raw.githubusercontent.com/antlr/grammars-v4/master/grammars.json";

interface ISample {
    examples: string[];
    example: string[];
}
function fetchGrammarSamples(onSuccess: (samples: GrammarSample[]) => void, onError: (reason: any) => void): void  {
    fetch(GRAMMAR_INDEX_URL)
        .then(async response => {
            const json = await response.json();
            (json as []).forEach(node => {
                const obj = node as ISample;
                obj.examples = obj.example;
                delete obj.example;
            });
            let samples: GrammarSample[] = json;
            samples = samples.sort( (s1, s2) => s1.name.toLowerCase().localeCompare(s2.name.toLowerCase(), 'en'));
            samples.unshift(SAMPLE_GRAMMAR);
            onSuccess(samples);
        })
        .catch(reason => {
            onError(reason);
        });
}

export { fetchGrammarSamples }

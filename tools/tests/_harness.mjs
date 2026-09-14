// Sandbox harness for the tools/*.mjs CLI tests. Run the suite with: node --test "tools/tests/**/*.test.mjs"
// Each script resolves REPO_ROOT from its OWN file location, so the only way to isolate a run from the
// real repo is to copy the script into a throwaway tree and spawn it there - never change cwd and hope.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const TESTS_DIR = path.dirname(fileURLToPath(import.meta.url));
const REAL_TOOLS_DIR = path.resolve(TESTS_DIR, '..');

export const SEED_DIR_REL = 'src/main/resources/db/seed';

/** Builds a throwaway repo tree containing copies of the named scripts, plus an empty seed directory.
 *  Nothing under the real repo is read or written by anything the returned `run()` spawns. */
export function sandbox(scriptNames) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mc-tools-'));
    const toolsDir = path.join(root, 'tools');
    const seedDir = path.join(root, SEED_DIR_REL);
    fs.mkdirSync(toolsDir, { recursive: true });
    fs.mkdirSync(seedDir, { recursive: true });

    for (const name of scriptNames) {
        fs.copyFileSync(path.join(REAL_TOOLS_DIR, name), path.join(toolsDir, name));
    }

    const abs = (rel) => path.join(root, rel);

    const api = {
        root,
        toolsDir,
        seedDir,
        abs,

        /** Writes a fixture file, creating parent directories as needed. */
        write(rel, contents) {
            const target = abs(rel);
            fs.mkdirSync(path.dirname(target), { recursive: true });
            fs.writeFileSync(target, contents, 'utf8');
            return target;
        },

        /** Writes a JSON fixture. Pass a string to deliberately produce malformed JSON. */
        writeJson(rel, value) {
            return api.write(rel, typeof value === 'string' ? value : JSON.stringify(value, null, 2));
        },

        /** Writes a newline-separated slug worklist. */
        writeSlugList(rel, slugs) {
            return api.write(rel, slugs.length === 0 ? '' : `${slugs.join('\n')}\n`);
        },

        read(rel) {
            return fs.readFileSync(abs(rel), 'utf8');
        },

        exists(rel) {
            return fs.existsSync(abs(rel));
        },

        /** Every file currently sitting in the sandbox seed directory, sorted. */
        seedFiles() {
            return fs.readdirSync(seedDir).sort();
        },

        /** Spawns the copied script with node and captures the full result. */
        run(scriptName, args = []) {
            const result = spawnSync(process.execPath, [path.join(toolsDir, scriptName), ...args], {
                encoding: 'utf8',
                cwd: root,
            });
            if (result.error) throw result.error;
            return { status: result.status, stdout: result.stdout, stderr: result.stderr };
        },

        cleanup() {
            fs.rmSync(root, { recursive: true, force: true });
        },
    };

    return api;
}

/** Creates a sandbox bound to the test's lifetime, removed automatically when the test finishes. */
export function sandboxFor(t, scriptNames) {
    const box = sandbox(scriptNames);
    t.after(() => box.cleanup());
    return box;
}

// --- generated-SQL readers -------------------------------------------------------------------

/** The raw "(n, 'slug', ...)" lines of the single INSERT ... VALUES block in a generated seed. */
export function insertRows(sql) {
    const match = sql.match(/VALUES\n([\s\S]*?);\n/);
    if (!match) throw new Error('no INSERT ... VALUES block found in the generated SQL');
    return match[1].split(',\n').map((line) => line.trim());
}

/** Decodes one staged row back into JS values, undoing the '' escaping so quoting can be asserted. */
export function sqlValues(row) {
    const inner = row.replace(/^\(/, '').replace(/\)$/, '');
    const out = [];
    let i = 0;
    while (i <= inner.length) {
        while (inner[i] === ' ') i++;
        if (inner[i] === "'") {
            let text = '';
            let j = i + 1;
            while (j < inner.length) {
                if (inner[j] === "'" && inner[j + 1] === "'") { text += "'"; j += 2; continue; }
                if (inner[j] === "'") break;
                text += inner[j];
                j++;
            }
            out.push(text);
            i = j + 1;
        } else {
            let j = inner.indexOf(',', i);
            if (j === -1) j = inner.length;
            const token = inner.slice(i, j).trim();
            if (token === '' && i >= inner.length) break;
            out.push(token === 'NULL' ? null : token);
            i = j;
        }
        const next = inner.indexOf(',', i);
        if (next === -1) break;
        i = next + 1;
    }
    return out;
}

/** Every staged row decoded, keyed in file order - the order the script chose to emit. */
export const stagedRows = (sql) => insertRows(sql).map(sqlValues);

/** The comment header as one unwrapped line, so an assertion need not know where the wrapper broke. */
export const headerProse = (sql) => sql.split('\nBEGIN;')[0].replace(/^--\s?/gm, '').replace(/\s+/g, ' ').trim();

// --- fixture builders ------------------------------------------------------------------------
// A source that matches none of the STRONG_SOURCES patterns, for the diameter-provenance rule.
export const WEAK_SOURCE = 'https://www.exemplo-agregador.net/fichas/moto';
export const STRONG_SOURCE = 'https://powersports.honda.com/motorcycle/specs';

export const brakeEntry = (overrides = {}) => ({
    slug: 'honda-cg-160-2020',
    front_brake: 'Single disc',
    rear_brake: 'Drum',
    source: WEAK_SOURCE,
    ...overrides,
});

export const tyreEntry = (overrides = {}) => ({
    slug: 'honda-cg-160-2020',
    front_tyre: '80/100-18',
    rear_tyre: '90/90-18',
    source: WEAK_SOURCE,
    ...overrides,
});

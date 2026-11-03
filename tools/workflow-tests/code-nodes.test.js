// Behaviour checks for n8n Code-node logic with mocked n8n globals (Node built-ins only, no npm).
// Run from the repo root: node tools/workflow-tests/code-nodes.test.js
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const root = process.argv[2] || path.resolve(__dirname, '..', '..');
const load = d => JSON.parse(fs.readFileSync(path.join(root, 'catalogue', 'automations', d, d + '.workflow.json'), 'utf8'));
const node = (wf, name) => wf.nodes.find(n => n.name === name).parameters.jsCode;
const runAll = (js, items, staticData, cfgPatch) => {
  const src = cfgPatch ? cfgPatch(js) : js;
  return new Function('$input', '$getWorkflowStaticData', src)({ all: () => items.map(j => ({ json: j })) }, () => staticData);
};
const runEach = (js, inputJson, refs) =>
  new Function('$input', '$', js)({ item: { json: inputJson } }, n => ({ item: { json: refs[n] } })).json;

const cle = load('classify-enquiry');
const dfu = load('draft-follow-up');
const event = { event: 'lead.created', eventId: 'e1', tenantId: 't', tenantName: 'T', leadId: 'l1',
  lead: { fields: { full_name: 'Asha', message: 'Is there a demo class this Saturday?', course_interest: 'Example Service A' } } };
const consent = js => js.replace('aiDataConsent: false', 'aiDataConsent: true');

// Prepare classification: consent off -> pending path; duplicate ignored; consent on -> LLM request.
let sd = {};
let out = runAll(node(cle, 'Prepare classification'), [{ body: event }], sd);
assert.strictEqual(out[0].json.callLlm, false);
assert.strictEqual(out[0].json.reason, 'no-ai-data-consent');
out = runAll(node(cle, 'Prepare classification'), [{ body: event }], sd);
assert.strictEqual(out[0].json.action, 'ignore');
out = runAll(node(cle, 'Prepare classification'), [{ body: { ...event, eventId: 'e2' } }], {}, consent);
const ctx = out[0].json;
assert.strictEqual(ctx.callLlm, true);
assert.ok(ctx.llmRequest.messages[0].content.includes('demo-request'));

const v = (resp, c = ctx) => runEach(node(cle, 'Validate labels'), resp, { 'Prepare classification': c });
const llm = obj => ({ choices: [{ message: { content: JSON.stringify(obj) } }] });
// CLE-01
let r = v(llm({ intent: 'demo-request', urgency: 'high', serviceInterest: 'Example Service A', confidence: 0.9 }));
assert.deepStrictEqual(r.classification, { status: 'classified', intent: 'demo-request', urgency: 'high', serviceInterest: 'Example Service A', confidence: 0.9 });
// CLE-02
r = v(llm({ intent: 'discount', urgency: 'asap', serviceInterest: 'Made up', confidence: 0.95 }));
assert.strictEqual(r.classification.status, 'needs-review');
assert.strictEqual(r.classification.intent, 'other');
assert.strictEqual(r.classification.serviceInterest, 'unknown');
r = v(llm({ intent: 'pricing', urgency: 'low', serviceInterest: 'unknown', confidence: 0.3 }));
assert.strictEqual(r.classification.intent, 'other');
assert.strictEqual(r.classification.status, 'needs-review');
// CLE-03
r = v({ error: { message: 'timeout of 20000ms exceeded' } });
assert.deepStrictEqual(r.classification, { status: 'pending' });
r = v({ choices: [{ message: { content: 'not json' } }] });
assert.strictEqual(r.classification.status, 'pending');
r = v(out[0].json, { ...ctx, callLlm: false, reason: 'no-ai-data-consent' });
assert.strictEqual(r.classification.status, 'pending');

// draft-follow-up
const classified = { event: 'lead.classified', eventId: 'e1', tenantId: 't', leadId: 'l1', fields: event.lead.fields,
  classification: { status: 'classified', intent: 'demo-request', urgency: 'high', serviceInterest: 'Example Service A', confidence: 0.9 } };
// DFU-03
assert.throws(() => runAll(node(dfu, 'Prepare draft'), [{ body: classified }], {}, js => js.replace('autoSend: false,', 'autoSend: true,')), /DFU-03/);
// DFU-01 template path
out = runAll(node(dfu, 'Prepare draft'), [{ body: classified }], {});
const dctx = out[0].json;
assert.strictEqual(dctx.callLlm, false);
assert.ok(dctx.template.body.startsWith('Hello Asha'));
assert.ok(dctx.template.subject.includes('Example Business'));
const choose = (resp, c) => runEach(node(dfu, 'Choose draft'), resp, { 'Prepare draft': c });
r = choose(dctx, dctx);
assert.strictEqual(r.draft.source, 'template');
// DFU-02 LLM output with forbidden claim falls back to template
const llmCtx = { ...dctx, callLlm: true };
r = choose({ choices: [{ message: { content: 'Hello Asha, selection is guaranteed!' } }] }, llmCtx);
assert.strictEqual(r.draft.source, 'template');
assert.strictEqual(r.draft.body, dctx.template.body);
r = choose({ choices: [{ message: { content: 'Hello Asha, thanks for asking about Saturday.' } }] }, llmCtx);
assert.strictEqual(r.draft.source, 'llm');
r = choose({ error: { message: 'timeout' } }, llmCtx);
assert.strictEqual(r.draft.source, 'template');
console.log('PASS workflow code-node checks (CLE-01/02/03, DFU-01/02/03)');

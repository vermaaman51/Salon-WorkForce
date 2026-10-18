// Applies to existing inputs and fields added dynamically by the form editors.
function configureMobile(input){
 input.pattern='[0-9]{10}';input.minLength=10;input.maxLength=10;input.inputMode='numeric';input.title='Enter exactly 10 digits.';
}
function validateField(input){
 if(!input.matches('input,textarea,select'))return;
 input.setCustomValidity('');
 if(input.matches('input[type=tel]')){configureMobile(input);if(input.value&&!/^[0-9]{10}$/.test(input.value))input.setCustomValidity('Enter a mobile number with exactly 10 digits.');}
 if(input.required&&input.matches('input:not([type=checkbox]):not([type=radio]),textarea')&&!input.value.trim())input.setCustomValidity('This field is required.');
}
document.querySelectorAll('input[type=tel]').forEach(configureMobile);
new MutationObserver(records=>{for(const record of records)for(const node of record.addedNodes)if(node.nodeType===1){if(node.matches('input[type=tel]'))configureMobile(node);node.querySelectorAll('input[type=tel]').forEach(configureMobile)}}).observe(document.body,{childList:true,subtree:true});
document.addEventListener('beforeinput',event=>{if(event.target.matches('input[type=tel]')&&event.data&&!/^[0-9]+$/.test(event.data))event.preventDefault()},true);
document.addEventListener('paste',event=>{const input=event.target;if(!input.matches('input[type=tel]'))return;event.preventDefault();configureMobile(input);const text=event.clipboardData.getData('text').trim();if(!/^[0-9]+$/.test(text))return;const start=input.selectionStart,end=input.selectionEnd,value=input.value.slice(0,start)+text+input.value.slice(end);if(value.length>10)return;input.value=value;input.setSelectionRange(start+text.length,start+text.length);validateField(input)},true);
document.addEventListener('input',event=>{const input=event.target;if(input.matches('input[type=tel]')){configureMobile(input);input.value=input.value.replace(/[^0-9]/g,'').slice(0,10)}validateField(input)},true);
document.addEventListener('blur',event=>validateField(event.target),true);
document.addEventListener('submit',event=>{const form=event.target;form.querySelectorAll('input,textarea,select').forEach(validateField);if(!form.reportValidity()){event.preventDefault();event.stopImmediatePropagation()}},true);

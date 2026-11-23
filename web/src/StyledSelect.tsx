import React, { useEffect, useId, useRef, useState } from 'react';

export type SelectOption = { value: string; label: string };
type Props = {
  id?: string;
  ariaLabel: string;
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
};

export function StyledSelect({ id, ariaLabel, value, options, onChange, disabled = false, placeholder = 'Chọn một mục' }: Props) {
  const generatedId = useId().replace(/:/g, '');
  const listId = id ?? 'styled-select-'+generatedId;
  const root = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const selectedIndex = options.findIndex(option => option.value === value);
  const [activeIndex, setActiveIndex] = useState(Math.max(0, selectedIndex));
  const selected = selectedIndex < 0 ? null : options[selectedIndex];

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);

  function choose(index: number) {
    const option = options[index];
    if (!option) return;
    onChange(option.value);
    setActiveIndex(index);
    setOpen(false);
  }

  function keyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      if (!open) {
        setActiveIndex(selectedIndex < 0 ? 0 : selectedIndex);
        setOpen(true);
      } else if (options.length) {
        setActiveIndex(current => (current + direction + options.length) % options.length);
      }
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (open) choose(activeIndex);
      else { setActiveIndex(selectedIndex < 0 ? 0 : selectedIndex); setOpen(true); }
    } else if (event.key === 'Home' && open && options.length) {
      event.preventDefault(); setActiveIndex(0);
    } else if (event.key === 'End' && open && options.length) {
      event.preventDefault(); setActiveIndex(options.length-1);
    } else if (event.key === 'Tab') setOpen(false);
  }

  return <div className="styled-select" ref={root}>
    <button id={id} type="button" className="styled-select-trigger" role="combobox" aria-label={ariaLabel} aria-expanded={open} aria-haspopup="listbox" aria-controls={listId+'-list'} aria-activedescendant={open&&options[activeIndex]?listId+'-option-'+activeIndex:undefined} disabled={disabled} onClick={() => { setActiveIndex(selectedIndex < 0 ? 0 : selectedIndex); setOpen(current => !current); }} onKeyDown={keyDown}>
      <span className={!selected?'is-placeholder':''}>{selected?.label ?? placeholder}</span>
      <svg className={'styled-select-chevron'+(open?' is-open':'')} viewBox="0 0 20 20" aria-hidden="true"><path d="m5 7.5 5 5 5-5"/></svg>
    </button>
    {open&&<div id={listId+'-list'} className="styled-select-menu" role="listbox" aria-label={ariaLabel}>{options.map((option,index)=><div id={listId+'-option-'+index} key={option.value} role="option" aria-selected={option.value===value} className={'styled-select-option'+(index===activeIndex?' is-active':'')} onMouseEnter={()=>setActiveIndex(index)} onMouseDown={event=>event.preventDefault()} onClick={()=>choose(index)}>{option.label}{option.value===value&&<svg viewBox="0 0 20 20" aria-hidden="true"><path d="m4 10 4 4 8-8"/></svg>}</div>)}</div>}
  </div>;
}

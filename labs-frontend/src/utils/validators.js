// Input sanitizers/validators for free-text fields. Mirrors
// HMS-frontend/src/utils/validators.js so phone/name handling behaves
// identically across the ZenoHosp suite — same bug class, same fix.
//
// labs has no purely-numeric ID fields or user-typed alphanumeric codes
// (UHID, accession numbers, report IDs are all system/HMS-generated), so
// this file only carries the phone/name sanitizers that apply here.

const validatePhone = (phone) => {
  if (!phone) return void 0;
  if (!/^\+?[\d\s\-()]{7,15}$/.test(phone)) return "Invalid phone number";
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 10 || digits.length > 13) return "Phone number must have 10 digits";
};

// Strips anything that isn't a phone-appropriate character as the user types,
// so free-text (letters, *&^% etc.) can't get into a phone field at all —
// validatePhone above is the submit-time backstop for what gets through.
const sanitizePhone = (raw) => raw.replace(/[^\d+\s\-()]/g, "").slice(0, 15);

// Letters (any script, so accented/transliterated names aren't broken) plus
// combining marks (\p{M} — Devanagari/Tamil/Kannada/etc. vowel signs are a
// SEPARATE Unicode category from the base letter; \p{L} alone strips them
// and corrupts the word, e.g. "अनिल" -> "अनल". Verified directly against the
// regex, not a browser/test-harness artifact.) plus spaces, apostrophes,
// periods and hyphens (O'Brien, Anne-Marie, Dr.) — no digits. Also
// capitalizes the letter right after the start of the string or a
// space/hyphen/apostrophe as you type (ravi -> Ravi, o'brien -> O'Brien,
// anne-marie -> Anne-Marie), leaving the rest of each word untouched so it
// never fights someone deliberately typing mixed case like "McDonald".
const sanitizeName = (raw) =>
  raw
    .replace(/[^\p{L}\p{M}\s'.-]/gu, "")
    .replace(/(^|[\s'-])(\p{L})/gu, (_, boundary, letter) => boundary + letter.toUpperCase());

export { sanitizeName, sanitizePhone, validatePhone };

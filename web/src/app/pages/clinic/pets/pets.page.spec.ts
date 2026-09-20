import {
  canRegisterPet,
  clinicsFromMemberships,
  mergeClinics,
  ownerPetPayload,
  PET_SPECIES,
  petCreateEndpoint,
  speciesLabelKey
} from './pets.page';

describe('Pet owner registration helpers', () => {
  it('shows the register action to pet owners and clinic staff, but not to platform admins', () => {
    expect(canRegisterPet(false, true, false)).toBeTrue();
    expect(canRegisterPet(true, false, false)).toBeTrue();
    expect(canRegisterPet(false, false, true)).toBeFalse();
    expect(canRegisterPet(false, false, false)).toBeFalse();
  });

  it('sends staff creates to /pets and owner creates to /pets/mine without an ownerId', () => {
    expect(petCreateEndpoint(true)).toBe('/pets');
    expect(petCreateEndpoint(false)).toBe('/pets/mine');
    expect(ownerPetPayload({
      tenantSlug: 'clinica-centro',
      name: 'Luna',
      species: 'DOG',
      breed: 'Mestizo',
      sex: 'FEMALE'
    })).toEqual({
      tenantSlug: 'clinica-centro',
      name: 'Luna',
      species: 'DOG',
      breed: 'Mestizo',
      sex: 'FEMALE'
    });
    expect(JSON.stringify(ownerPetPayload({
      tenantSlug: 'clinica-centro',
      name: 'Luna',
      species: 'DOG',
      breed: '',
      sex: 'UNKNOWN'
    }))).not.toContain('ownerId');
  });

  it('merges membership clinics with the public catalog and keeps a species list', () => {
    expect(mergeClinics(
      clinicsFromMemberships([{ id: 1, slug: 'san-martin', name: 'San Martín', commercialName: 'San Martín Vet', role: 'PET_OWNER' }]),
      [{ slug: 'huellitas', name: 'Huellitas' }, { slug: 'san-martin', name: 'Duplicate' }]
    ).map(c => c.slug)).toEqual(['huellitas', 'san-martin']);
    expect(PET_SPECIES).toEqual(['DOG', 'CAT', 'BIRD', 'RABBIT', 'RODENT', 'REPTILE', 'HORSE', 'OTHER']);
    expect(speciesLabelKey('DOG')).toBe('pets.speciesOptions.DOG');
    expect(speciesLabelKey('cat')).toBe('pets.speciesOptions.CAT');
    expect(speciesLabelKey('unknown-species')).toBe('');
  });
});

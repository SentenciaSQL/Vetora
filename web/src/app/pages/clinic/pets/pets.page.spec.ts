import { canRegisterPet, ownerPetPayload, petCreateEndpoint } from './pets.page';

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
});
